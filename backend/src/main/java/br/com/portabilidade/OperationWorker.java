package br.com.portabilidade;

import br.com.portabilidade.Models.*;
import java.time.*;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "api")
public class OperationWorker {
  private static final Logger log = LoggerFactory.getLogger(OperationWorker.class);
  private final Store store;
  private final CentralClient central;
  private final TransactionTemplate tx;
  private final long base, max;
  private final int maxAttempts;

  public OperationWorker(
      Store store,
      CentralClient central,
      TransactionTemplate tx,
      @Value("${app.retry-base-ms}") long base,
      @Value("${app.retry-max-ms}") long max,
      @Value("${app.max-attempts}") int maxAttempts) {
    this.store = store;
    this.central = central;
    this.tx = tx;
    this.base = base;
    this.max = max;
    this.maxAttempts = maxAttempts;
  }

  @Scheduled(
      fixedDelayString = "${app.worker-delay-ms}",
      initialDelayString = "${app.worker-delay-ms}")
  public void tick() {
    var ids =
        store.db.queryForList(
            "SELECT id FROM operations WHERE status IN ('RECEBIDA','PENDENTE') AND"
                + " next_attempt_at<=? ORDER BY next_attempt_at LIMIT 20",
            String.class,
            Store.ts(Instant.now()));
    for (String id : ids) {
      try {
        process(id);
      } catch (RuntimeException e) {
        log.error("Worker não concluiu operação {}: {}", id, e.getClass().getSimpleName());
      }
    }
  }

  public long backoff(int attempt, long retryAfter) {
    long exponential = Math.min(max, base * (1L << Math.min(20, Math.max(0, attempt - 1))));
    long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, exponential / 4));
    return Math.max(retryAfter, Math.min(max, exponential + jitter));
  }

  public void process(String id) {
    tx.executeWithoutResult(
        s -> {
          // Recheck under lock: polling candidates can be read simultaneously by multiple
          // instances.
          var rows = store.db.queryForList("SELECT * FROM operations WHERE id=? FOR UPDATE", id);
          if (rows.isEmpty()) return;
          var op = store.operation(id);
          if (!java.util.Set.of("RECEBIDA", "PENDENTE").contains(op.status())
              || op.proximaTentativa().isAfter(Instant.now())) return;
          int attempt = op.tentativas() + 1;
          try {
            if (op.protocolo() == null) {
              String key = (String) rows.getFirst().get("idempotency_key");
              String protocol = central.submit(op.solicitacao(), key);
              store.db.update(
                  "UPDATE operations SET"
                      + " status='PENDENTE',protocol=?,attempts=0,last_error=NULL,next_attempt_at=?,updated_at=?"
                      + " WHERE id=?",
                  protocol,
                  Store.ts(Instant.now().plusMillis(base)),
                  Store.ts(Instant.now()),
                  id);
              store.event(id, "ACEITA_CENTRAL", "Central respondeu 202. Protocolo: " + protocol);
            } else {
              var poll = central.poll(op.protocolo());
              String status = poll.result().status();
              if (status.equals("CONFIRMADA")) {
                for (String installment : op.solicitacao().parcelas()) {
                  store.db.update(
                      "INSERT INTO"
                          + " receiving_accounts(beneficiary,installment_id,account_json,operation_id)"
                          + " VALUES(?,?,?,?)",
                      op.beneficiarioId(),
                      installment,
                      store.json.write(op.solicitacao().contaRecebimento()),
                      id);
                }
                terminal(id, "CONFIRMADA", null, attempt);
                store.event(
                    id,
                    "CONTA_ATUALIZADA",
                    "Conta de recebimento atualizada para as parcelas confirmadas.");
              } else if (status.equals("REJEITADA")) {
                String reason =
                    poll.result().motivoRejeicao() == null
                        ? "Central rejeitou a solicitação."
                        : poll.result().motivoRejeicao();
                terminal(id, "REJEITADA", reason, attempt);
                store.db.update("DELETE FROM reservations WHERE operation_id=?", id);
              } else {
                schedule(
                    id,
                    attempt,
                    "Central ainda está processando a portabilidade.",
                    poll.retryAfterMs(),
                    "CONSULTA_PENDENTE",
                    true);
              }
            }
          } catch (CentralClient.IntegrationException e) {
            schedule(
                id, attempt, e.getMessage(), e.retryAfterMs(), "FALHA_INTEGRACAO", e.retryable());
          }
        });
  }

  private void terminal(String id, String status, String reason, int attempt) {
    store.db.update(
        "UPDATE operations SET status=?,last_error=?,attempts=?,updated_at=? WHERE id=?",
        status,
        reason,
        attempt,
        Store.ts(Instant.now()),
        id);
    store.event(id, status, reason == null ? "Central confirmou a portabilidade." : reason);
  }

  private void schedule(
      String id, int attempt, String detail, long retryAfter, String type, boolean retryable) {
    store.event(id, type, detail);
    if (!retryable || attempt >= maxAttempts) {
      store.db.update(
          "UPDATE operations SET status='REVISAO_NECESSARIA',attempts=?,last_error=?,updated_at=?"
              + " WHERE id=?",
          attempt,
          detail,
          Store.ts(Instant.now()),
          id);
      store.event(
          id,
          "REVISAO_NECESSARIA",
          "Tentativas automáticas encerradas; resultado remoto preservado para retomada segura.");
    } else {
      long delay = backoff(attempt, retryAfter);
      store.db.update(
          "UPDATE operations SET attempts=?,last_error=?,next_attempt_at=?,updated_at=? WHERE id=?",
          attempt,
          type.equals("FALHA_INTEGRACAO") ? detail : null,
          Store.ts(Instant.now().plusMillis(delay)),
          Store.ts(Instant.now()),
          id);
      store.event(
          id,
          "REAGENDADA",
          "Nova tentativa em " + delay + " ms (backoff com jitter e Retry-After).");
    }
  }
}
