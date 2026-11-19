package com.payrollportability.application.port.outbound;

import com.payrollportability.domain.Models.Authorization;
import com.payrollportability.domain.Models.AuthorizationRequest;
import com.payrollportability.domain.Models.CentralStatus;
import com.payrollportability.domain.Models.Installment;
import com.payrollportability.domain.Models.OperationRequest;
import java.util.List;

public interface CentralGateway {
  Authorization authorize(AuthorizationRequest request);

  List<Installment> agenda(String beneficiary);

  String submit(OperationRequest request, String key);

  Poll poll(String protocol);

  record Poll(CentralStatus result, long retryAfterMs) {}

  class IntegrationException extends RuntimeException {
    private final boolean retryable;
    private final long retryAfterMs;

    public IntegrationException(String message, boolean retryable, long retryAfterMs) {
      super(message);
      this.retryable = retryable;
      this.retryAfterMs = retryAfterMs;
    }

    public boolean retryable() {
      return retryable;
    }

    public long retryAfterMs() {
      return retryAfterMs;
    }
  }
}
