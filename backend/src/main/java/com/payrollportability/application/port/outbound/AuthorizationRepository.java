package com.payrollportability.application.port.outbound;

import com.payrollportability.domain.Models.Authorization;
import java.util.Optional;

public interface AuthorizationRepository {
  Optional<Authorization> authorization(String beneficiary, String agreement);

  void saveAuthorization(Authorization authorization);

  boolean authorized(String beneficiary);

  boolean agreementAuthorized(String beneficiary, String agreement);
}
