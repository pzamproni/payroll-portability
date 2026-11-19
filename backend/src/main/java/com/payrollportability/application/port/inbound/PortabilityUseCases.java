package com.payrollportability.application.port.inbound;

import com.payrollportability.domain.Models.Authorization;
import com.payrollportability.domain.Models.AuthorizationRequest;
import com.payrollportability.domain.Models.Installment;
import com.payrollportability.domain.Models.Operation;
import com.payrollportability.domain.Models.OperationRequest;
import java.util.List;

public interface PortabilityUseCases {
  Authorization authorize(AuthorizationRequest request);

  List<Installment> agenda(String beneficiary);

  Operation create(String key, OperationRequest request);

  Operation resume(String id);

  Operation get(String id);

  List<Operation> list();
}
