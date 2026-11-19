package com.payrollportability.application.port.outbound;

import com.payrollportability.domain.Models.Account;
import com.payrollportability.domain.Models.Operation;
import com.payrollportability.domain.Models.OperationRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OperationRepository {
  Optional<Operation> findByKey(String key);

  Operation operation(String id);

  List<Operation> all();

  void insertIntent(String id, String key, OperationRequest request, Instant now);

  void reserve(String beneficiary, String installment, String operation);

  void event(String id, String type, String detail);

  boolean lock(String id);

  String idempotencyKey(String id);

  List<String> dueIds(Instant now);

  void accepted(String id, String protocol, Instant next, Instant now);

  void updateAccount(String beneficiary, String installment, Account account, String operation);

  void terminal(String id, String status, String reason, int attempt, Instant now);

  void releaseReservations(String id);

  void review(String id, int attempt, String error, Instant now);

  void retry(String id, int attempt, String error, Instant next, Instant now);

  void resume(String id, String status, Instant now);
}
