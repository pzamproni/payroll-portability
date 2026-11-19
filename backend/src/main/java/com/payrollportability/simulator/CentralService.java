package com.payrollportability.simulator;

import com.payrollportability.domain.DomainException;
import com.payrollportability.domain.Models.Account;
import com.payrollportability.domain.Models.Authorization;
import com.payrollportability.domain.Models.AuthorizationRequest;
import com.payrollportability.domain.Models.CentralStatus;
import com.payrollportability.domain.Models.Installment;
import com.payrollportability.domain.Models.OperationRequest;
import com.payrollportability.infrastructure.security.Hmac;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "central")
public class CentralService {
  private final SimulatorStore store;
  private final TransactionTemplate tx;
  private final long delay;

  public CentralService(
      SimulatorStore store,
      TransactionTemplate tx,
      @Value("${app.confirmation-delay-ms}") long delay) {
    this.store = store;
    this.tx = tx;
    this.delay = delay;
  }

  private Authorization existingAuth(AuthorizationRequest request) {
    var rows =
        store.db.query(
            "SELECT * FROM authorizations WHERE beneficiary=? AND agreement=?",
            (rs, n) ->
                new Authorization(
                    rs.getString("id"), request.beneficiarioId(), request.convenioId()),
            request.beneficiarioId(),
            request.convenioId());
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public Authorization authorize(AuthorizationRequest request) {
    var existing = existingAuth(request);
    if (existing != null) return existing;
    var result =
        new Authorization(
            UUID.randomUUID().toString(), request.beneficiarioId(), request.convenioId());
    try {
      tx.executeWithoutResult(
          s -> {
            store.db.update(
                "INSERT INTO authorizations(id,beneficiary,agreement,created_at) VALUES(?,?,?,?)",
                result.id(),
                result.beneficiarioId(),
                result.convenioId(),
                SimulatorStore.ts(Instant.now()));
            for (int i = 1; i <= 6; i++) {
              String id =
                  UUID.nameUUIDFromBytes(
                          (request.beneficiarioId() + ":" + request.convenioId() + ":" + i)
                              .getBytes(StandardCharsets.UTF_8))
                      .toString();
              store.db.update(
                  "INSERT INTO"
                      + " central_installments(id,beneficiary,agreement,amount,due_date,status)"
                      + " VALUES(?,?,?,?,?,?)",
                  id,
                  request.beneficiarioId(),
                  request.convenioId(),
                  new BigDecimal("320.00").add(BigDecimal.valueOf(i * 25L)),
                  LocalDate.now(ZoneOffset.UTC).plusMonths(i),
                  i == 5 ? "PAGA" : i == 6 ? "BLOQUEADA" : "ABERTA");
            }
          });
    } catch (DataIntegrityViolationException e) {
      existing = existingAuth(request);
      if (existing != null) return existing;
      throw e;
    }
    return result;
  }

  public List<Installment> agenda(String beneficiary) {
    if (!store.authorized(beneficiary))
      throw new DomainException(
          DomainException.Kind.UNAUTHORIZED, "SEM_AUTORIZACAO", "Beneficiário sem autorização.");
    return store.db.query(
        "SELECT * FROM central_installments WHERE beneficiary=? AND due_date>? ORDER BY"
            + " due_date,id",
        (rs, n) ->
            new Installment(
                rs.getString("id"),
                rs.getString("agreement"),
                rs.getBigDecimal("amount"),
                rs.getDate("due_date").toLocalDate(),
                rs.getString("status"),
                rs.getString("status").equals("ABERTA"),
                rs.getString("account_json") == null
                    ? null
                    : store.json.read(rs.getString("account_json"), Account.class)),
        beneficiary,
        java.sql.Date.valueOf(LocalDate.now(ZoneOffset.UTC)));
  }

  public int attempt(String key) {
    // Separate committed transaction: fault injection must survive the deliberately failed request.
    try {
      store.db.update("INSERT INTO simulation_attempts(idempotency_key,attempts) VALUES(?,0)", key);
    } catch (DataIntegrityViolationException ignored) {
      /* Existing deterministic scenario counter. */
    }
    return tx.execute(
        s -> {
          int previous =
              store.db.queryForObject(
                  "SELECT attempts FROM simulation_attempts WHERE idempotency_key=? FOR UPDATE",
                  Integer.class,
                  key);
          store.db.update(
              "UPDATE simulation_attempts SET attempts=? WHERE idempotency_key=?",
              previous + 1,
              key);
          return previous + 1;
        });
  }

  private String existing(String key, String hash) {
    var rows =
        store.db.queryForList(
            "SELECT protocol,payload_hash FROM central_operations WHERE idempotency_key=?", key);
    if (rows.isEmpty()) return null;
    if (!hash.equals(rows.getFirst().get("payload_hash")))
      throw new DomainException(
          DomainException.Kind.CONFLICT,
          "CHAVE_REUTILIZADA",
          "Conteúdo diferente para a mesma chave na Central.");
    return (String) rows.getFirst().get("protocol");
  }

  public String submit(String key, OperationRequest raw) {
    var request = raw.normalized();
    String payload = store.json.write(request), hash = Hmac.hash(payload);
    String existing = existing(key, hash);
    if (existing != null) return existing;
    try {
      return tx.execute(
          s -> {
            // Lock in canonical order, then recheck the key before current eligibility.
            var installments = new ArrayList<Map<String, Object>>();
            for (String id : request.parcelas()) {
              var rows =
                  store.db.queryForList(
                      "SELECT * FROM central_installments WHERE id=? AND beneficiary=? FOR UPDATE",
                      id,
                      request.beneficiarioId());
              if (rows.isEmpty())
                throw new DomainException(
                    DomainException.Kind.CONFLICT,
                    "PARCELA_INVALIDA",
                    "Parcela não pertence ao beneficiário.");
              installments.add(rows.getFirst());
            }
            String again = existing(key, hash);
            if (again != null) return again;
            for (var row : installments) {
              if (!store.agreementAuthorized(
                      request.beneficiarioId(), (String) row.get("agreement"))
                  || !"ABERTA".equals(row.get("status"))
                  || !((java.sql.Date) row.get("due_date"))
                      .toLocalDate()
                      .isAfter(LocalDate.now(ZoneOffset.UTC)))
                throw new DomainException(
                    DomainException.Kind.CONFLICT,
                    "PARCELA_INELEGIVEL",
                    "Parcela inelegível na Central.");
            }
            String protocol = UUID.randomUUID().toString();
            store.db.update(
                "INSERT INTO"
                    + " central_operations(protocol,idempotency_key,payload_hash,payload,beneficiary,scenario,status,confirm_at)"
                    + " VALUES(?,?,?,?,?,?,'PENDENTE',?)",
                protocol,
                key,
                hash,
                payload,
                request.beneficiarioId(),
                request.cenario().name(),
                SimulatorStore.ts(Instant.now().plusMillis(delay)));
            for (String id : request.parcelas())
              store.db.update(
                  "INSERT INTO central_reservations(installment_id,protocol) VALUES(?,?)",
                  id,
                  protocol);
            return protocol;
          });
    } catch (DataIntegrityViolationException e) {
      existing = existing(key, hash);
      if (existing != null) return existing;
      throw new DomainException(
          DomainException.Kind.CONFLICT, "PARCELA_RESERVADA", "Parcela já reservada na Central.");
    }
  }

  public CentralStatus poll(String protocol) {
    return tx.execute(
        s -> {
          var rows =
              store.db.queryForList(
                  "SELECT * FROM central_operations WHERE protocol=? FOR UPDATE", protocol);
          if (rows.isEmpty())
            throw new DomainException(
                DomainException.Kind.NOT_FOUND, "PROTOCOLO_INVALIDO", "Protocolo não encontrado.");
          var row = rows.getFirst();
          String status = (String) row.get("status"), reason = (String) row.get("reason");
          Instant confirmAt =
              store.db.queryForObject(
                  "SELECT confirm_at FROM central_operations WHERE protocol=?",
                  (rs, n) -> rs.getTimestamp(1).toInstant(),
                  protocol);
          // H2/PostgreSQL return different JDBC temporal representations through queryForList.
          if (status.equals("PENDENTE") && !confirmAt.isAfter(Instant.now())) {
            status = "REJECT".equals(row.get("scenario")) ? "REJEITADA" : "CONFIRMADA";
            reason =
                status.equals("REJEITADA")
                    ? "Beneficiário não atende à política de portabilidade do convênio."
                    : null;
            store.db.update(
                "UPDATE central_operations SET status=?,reason=? WHERE protocol=?",
                status,
                reason,
                protocol);
            if (status.equals("CONFIRMADA")) {
              var request = store.json.read((String) row.get("payload"), OperationRequest.class);
              for (String id : request.parcelas())
                store.db.update(
                    "UPDATE central_installments SET status='PORTADA',account_json=? WHERE id=?",
                    store.json.write(request.contaRecebimento()),
                    id);
            } else store.db.update("DELETE FROM central_reservations WHERE protocol=?", protocol);
          }
          return new CentralStatus(status, reason);
        });
  }
}
