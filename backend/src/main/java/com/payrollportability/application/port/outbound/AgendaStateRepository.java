package com.payrollportability.application.port.outbound;

import com.payrollportability.domain.Models.Account;
import java.util.Optional;
import java.util.Set;

public interface AgendaStateRepository {
  Set<String> reservedInstallments(String beneficiary);

  Optional<Account> receivingAccount(String beneficiary, String installment);
}
