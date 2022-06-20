package com.axelor.apps.account.service.invoice;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceTerm;
import com.axelor.apps.account.db.PfpPartialReason;
import com.axelor.apps.account.db.SubstitutePfpValidator;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.InvoiceTermRepository;
import com.axelor.apps.base.db.CancelReason;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class InvoiceTermPfpServiceImpl implements InvoiceTermPfpService {
  protected InvoiceTermService invoiceTermService;
  protected InvoiceService invoiceService;
  protected InvoiceTermRepository invoiceTermRepo;
  protected InvoiceRepository invoiceRepo;

  @Inject
  public InvoiceTermPfpServiceImpl(
      InvoiceTermService invoiceTermService,
      InvoiceService invoiceService,
      InvoiceTermRepository invoiceTermRepo,
      InvoiceRepository invoiceRepo) {
    this.invoiceTermService = invoiceTermService;
    this.invoiceService = invoiceService;
    this.invoiceTermRepo = invoiceTermRepo;
    this.invoiceRepo = invoiceRepo;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void validatePfp(InvoiceTerm invoiceTerm, User currentUser) {
    invoiceTerm.setDecisionPfpTakenDate(
        Beans.get(AppBaseService.class).getTodayDate(invoiceTerm.getInvoice().getCompany()));
    invoiceTerm.setPfpGrantedAmount(invoiceTerm.getAmount());
    invoiceTerm.setPfpValidateStatusSelect(InvoiceTermRepository.PFP_STATUS_VALIDATED);
    invoiceTerm.setPfpValidatorUser(currentUser);
    invoiceTermRepo.save(invoiceTerm);

    this.checkOtherInvoiceTerms(invoiceTerm);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Integer massValidatePfp(List<Long> invoiceTermIds) {
    List<InvoiceTerm> invoiceTermList =
        invoiceTermRepo
            .all()
            .filter(
                "self.id in ? AND self.pfpValidateStatusSelect != ?",
                invoiceTermIds,
                InvoiceTermRepository.PFP_STATUS_VALIDATED)
            .fetch();
    User currentUser = AuthUtils.getUser();
    int updatedRecords = 0;
    for (InvoiceTerm invoiceTerm : invoiceTermList) {
      if (canUpdateInvoiceTerm(invoiceTerm, currentUser)) {
        validatePfp(invoiceTerm, currentUser);
        updatedRecords++;
      }
    }
    return updatedRecords;
  }

  protected boolean canUpdateInvoiceTerm(InvoiceTerm invoiceTerm, User currentUser) {
    boolean isValidUser =
        currentUser.getIsSuperPfpUser()
            || (ObjectUtils.notEmpty(invoiceTerm.getPfpValidatorUser())
                && currentUser.equals(invoiceTerm.getPfpValidatorUser()));
    if (isValidUser) {
      return true;
    }
    return validateUser(invoiceTerm, currentUser)
        && (ObjectUtils.notEmpty(invoiceTerm.getPfpValidatorUser())
            && invoiceTerm
                .getPfpValidatorUser()
                .equals(invoiceService.getPfpValidatorUser(invoiceTerm.getInvoice())))
        && !invoiceTerm.getIsPaid();
  }

  protected boolean validateUser(InvoiceTerm invoiceTerm, User currentUser) {
    if (ObjectUtils.notEmpty(invoiceTerm.getPfpValidatorUser())) {
      List<SubstitutePfpValidator> substitutePfpValidatorList =
          invoiceTerm.getPfpValidatorUser().getSubstitutePfpValidatorList();
      LocalDate todayDate =
          Beans.get(AppBaseService.class).getTodayDate(invoiceTerm.getInvoice().getCompany());

      for (SubstitutePfpValidator substitutePfpValidator : substitutePfpValidatorList) {
        if (substitutePfpValidator.getSubstitutePfpValidatorUser().equals(currentUser)) {
          LocalDate substituteStartDate = substitutePfpValidator.getSubstituteStartDate();
          LocalDate substituteEndDate = substitutePfpValidator.getSubstituteEndDate();
          if (substituteStartDate == null) {
            if (substituteEndDate == null || substituteEndDate.isAfter(todayDate)) {
              return true;
            }
          } else {
            if (substituteEndDate == null && substituteStartDate.isBefore(todayDate)) {
              return true;
            } else if (substituteStartDate.isBefore(todayDate)
                && substituteEndDate.isAfter(todayDate)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  @Override
  public Integer massRefusePfp(
      List<Long> invoiceTermIds,
      CancelReason reasonOfRefusalToPay,
      String reasonOfRefusalToPayStr) {
    List<InvoiceTerm> invoiceTermList =
        invoiceTermRepo
            .all()
            .filter(
                "self.id in ? AND self.pfpValidateStatusSelect != ?",
                invoiceTermIds,
                InvoiceTermRepository.PFP_STATUS_LITIGATION)
            .fetch();
    User currentUser = AuthUtils.getUser();
    int updatedRecords = 0;
    for (InvoiceTerm invoiceTerm : invoiceTermList) {
      boolean invoiceTermCheck =
          ObjectUtils.notEmpty(invoiceTerm.getInvoice())
              && ObjectUtils.notEmpty(invoiceTerm.getInvoice().getCompany())
              && ObjectUtils.notEmpty(reasonOfRefusalToPay);
      if (invoiceTermCheck && canUpdateInvoiceTerm(invoiceTerm, currentUser)) {
        refusalToPay(invoiceTerm, reasonOfRefusalToPay, reasonOfRefusalToPayStr);
        updatedRecords++;
      }
    }
    return updatedRecords;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void refusalToPay(
      InvoiceTerm invoiceTerm, CancelReason reasonOfRefusalToPay, String reasonOfRefusalToPayStr) {
    invoiceTerm.setPfpValidateStatusSelect(InvoiceTermRepository.PFP_STATUS_LITIGATION);
    invoiceTerm.setDecisionPfpTakenDate(
        Beans.get(AppBaseService.class).getTodayDate(invoiceTerm.getInvoice().getCompany()));
    invoiceTerm.setPfpGrantedAmount(BigDecimal.ZERO);
    invoiceTerm.setPfpRejectedAmount(invoiceTerm.getAmount());
    invoiceTerm.setPfpValidatorUser(AuthUtils.getUser());
    invoiceTerm.setReasonOfRefusalToPay(reasonOfRefusalToPay);
    invoiceTerm.setReasonOfRefusalToPayStr(
        reasonOfRefusalToPayStr != null ? reasonOfRefusalToPayStr : reasonOfRefusalToPay.getName());

    invoiceTermRepo.save(invoiceTerm);

    this.checkOtherInvoiceTerms(invoiceTerm);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void generateInvoiceTerm(
      InvoiceTerm originalInvoiceTerm,
      BigDecimal invoiceAmount,
      BigDecimal pfpGrantedAmount,
      PfpPartialReason partialReason) {
    BigDecimal amount = invoiceAmount.subtract(pfpGrantedAmount);
    Invoice invoice = originalInvoiceTerm.getInvoice();
    invoiceTermService.createInvoiceTerm(originalInvoiceTerm, invoice, amount);
    updateOriginalTerm(originalInvoiceTerm, pfpGrantedAmount, partialReason, amount, invoice);

    invoiceTermService.initInvoiceTermsSequence(originalInvoiceTerm.getInvoice());
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void updateOriginalTerm(
      InvoiceTerm originalInvoiceTerm,
      BigDecimal pfpGrantedAmount,
      PfpPartialReason partialReason,
      BigDecimal amount,
      Invoice invoice) {
    originalInvoiceTerm.setIsCustomized(true);
    originalInvoiceTerm.setIsPaid(false);
    originalInvoiceTerm.setAmount(pfpGrantedAmount);
    originalInvoiceTerm.setPercentage(
        invoiceTermService.computeCustomizedPercentage(pfpGrantedAmount, invoice.getInTaxTotal()));
    originalInvoiceTerm.setAmountRemaining(pfpGrantedAmount);
    originalInvoiceTerm.setPfpValidateStatusSelect(
        InvoiceTermRepository.PFP_STATUS_PARTIALLY_VALIDATED);
    originalInvoiceTerm.setPfpGrantedAmount(pfpGrantedAmount);
    originalInvoiceTerm.setPfpRejectedAmount(amount);
    originalInvoiceTerm.setDecisionPfpTakenDate(LocalDate.now());
    originalInvoiceTerm.setPfpPartialReason(partialReason);
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void checkOtherInvoiceTerms(InvoiceTerm invoiceTerm) {
    Invoice invoice = invoiceTerm.getInvoice();

    if (invoice == null) {
      return;
    }

    int pfpStatus = this.getPfpValidateStatusSelect(invoiceTerm);
    int otherPfpStatus;
    for (InvoiceTerm otherInvoiceTerm : invoice.getInvoiceTermList()) {
      if (!otherInvoiceTerm.getId().equals(invoiceTerm.getId())) {
        otherPfpStatus = this.getPfpValidateStatusSelect(otherInvoiceTerm);

        if (otherPfpStatus != pfpStatus) {
          pfpStatus = InvoiceTermRepository.PFP_STATUS_AWAITING;
          break;
        }
      }
    }

    invoice.setPfpValidateStatusSelect(pfpStatus);
    invoiceRepo.save(invoice);
  }

  protected int getPfpValidateStatusSelect(InvoiceTerm invoiceTerm) {
    if (invoiceTerm.getPfpValidateStatusSelect()
        == InvoiceTermRepository.PFP_STATUS_PARTIALLY_VALIDATED) {
      return InvoiceTermRepository.PFP_STATUS_VALIDATED;
    } else {
      return invoiceTerm.getPfpValidateStatusSelect();
    }
  }
}
