/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2024 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.apps.contract.service;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.contract.db.Contract;
import com.axelor.apps.contract.db.ContractLine;
import java.math.BigDecimal;

public interface CurrencyScaleServiceContract {

  BigDecimal getScaledValue(Contract contract, BigDecimal amount);

  BigDecimal getCompanyScaledValue(Contract contract, BigDecimal amount);

  BigDecimal getScaledValue(ContractLine contractLine, BigDecimal amount);

  BigDecimal getCompanyScaledValue(ContractLine contractLine, BigDecimal amount);

  int getScale(Contract contract);

  int getCompanyScale(Company company);

  int getScale(Currency currency);
}
