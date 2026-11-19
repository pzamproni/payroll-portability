package com.payrollportability.application.service;

import com.payrollportability.application.port.inbound.OperationProcessing;
import com.payrollportability.application.port.outbound.CentralGateway;
import com.payrollportability.application.port.outbound.OperationRepository;
import com.payrollportability.application.port.outbound.UnitOfWork;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Durable workflow driven through ports; no scheduler, SQL or HTTP implementation details. */
public class OperationProcessor implements OperationProcessing {
  private final OperationRepository operations;
  private final CentralGateway central;
  private final UnitOfWork tx;
  private final RetryPolicy retry;

  public OperationProcessor(
      OperationRepository operations, CentralGateway central, UnitOfWork tx, RetryPolicy retry) {
    this.operations = operations;
    this.central = central;
    this.tx = tx;
    this.retry = retry;
  }

  public List<String> dueIds() {
    return operations.dueIds(Instant.now());
  }

  public long backoff(int attempt, long retryAfter) {
    return retry.delay(attempt, retryAfter);
  }

  public void process(String id) {
    tx.execute(
        () -> {
          if (!operations.lock(id)) return;
          var op = operations.operation(id);
          if (!Set.of("RECEBIDA", "PENDENTE").contains(op.status())
              || op.proximaTentativa().isAfter(Instant.now())) return;
          int attempt = op.tentativas() + 1;
          try {
            if (op.protocolo() == null) {
              String protocol = central.submit(op.solicitacao(), operations.idempotencyKey(id));
              operations.accepted(
                  id, protocol, Instant.now().plusMillis(retry.baseMs()), Instant.now());
              operations.event(
                  id, "ACEITA_CENTRAL", "Central respondeu 202. Protocolo: " + protocol);
            } else {
              var poll = central.poll(op.protocolo());
              String status = poll.result().status();
              if (status.equals("CONFIRMADA")) {
                for (String installment : op.solicitacao().parcelas())
                  operations.updateAccount(
                      op.beneficiarioId(), installment, op.solicitacao().contaRecebimento(), id);
                terminal(id, "CONFIRMADA", null, attempt);
                operations.event(
                    id,
                    "CONTA_ATUALIZADA",
                    "Conta de recebimento atualizada para as parcelas confirmadas.");
              } else if (status.equals("REJEITADA")) {
                String reason =
                    poll.result().motivoRejeicao() == null
                        ? "Central rejeitou a solicitação."
                        : poll.result().motivoRejeicao();
                terminal(id, "REJEITADA", reason, attempt);
                operations.releaseReservations(id);
              } else
                schedule(
                    id,
                    attempt,
                    "Central ainda está processando a portabilidade.",
                    poll.retryAfterMs(),
                    "CONSULTA_PENDENTE",
                    true);
            }
          } catch (CentralGateway.IntegrationException e) {
            schedule(
                id, attempt, e.getMessage(), e.retryAfterMs(), "FALHA_INTEGRACAO", e.retryable());
          }
        });
  }

  private void terminal(String id, String status, String reason, int attempt) {
    operations.terminal(id, status, reason, attempt, Instant.now());
    operations.event(id, status, reason == null ? "Central confirmou a portabilidade." : reason);
  }

  private void schedule(
      String id, int attempt, String detail, long retryAfter, String type, boolean retryable) {
    operations.event(id, type, detail);
    if (!retryable || attempt >= retry.maxAttempts()) {
      operations.review(id, attempt, detail, Instant.now());
      operations.event(
          id,
          "REVISAO_NECESSARIA",
          "Tentativas automáticas encerradas; resultado remoto preservado para retomada segura.");
    } else {
      long delay = backoff(attempt, retryAfter);
      operations.retry(
          id,
          attempt,
          type.equals("FALHA_INTEGRACAO") ? detail : null,
          Instant.now().plusMillis(delay),
          Instant.now());
      operations.event(
          id,
          "REAGENDADA",
          "Nova tentativa em " + delay + " ms (backoff com jitter e Retry-After).");
    }
  }
}
