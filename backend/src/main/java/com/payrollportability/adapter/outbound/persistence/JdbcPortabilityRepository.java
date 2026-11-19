package com.payrollportability.adapter.outbound.persistence;

import com.payrollportability.application.port.outbound.AgendaStateRepository;
import com.payrollportability.application.port.outbound.AuthorizationRepository;
import com.payrollportability.application.port.outbound.OperationRepository;
import com.payrollportability.domain.DomainException;
import com.payrollportability.domain.Models.Account;
import com.payrollportability.domain.Models.Authorization;
import com.payrollportability.domain.Models.Event;
import com.payrollportability.domain.Models.Operation;
import com.payrollportability.domain.Models.OperationRequest;
import com.payrollportability.domain.PersistenceConflict;
import com.payrollportability.infrastructure.json.Json;
import com.payrollportability.infrastructure.security.Hmac;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "api")
public class JdbcPortabilityRepository
    implements AuthorizationRepository, AgendaStateRepository, OperationRepository {
  private final JdbcTemplate db;
  private final Json json;

  public JdbcPortabilityRepository(JdbcTemplate db, Json json) {
    this.db = db;
    this.json = json;
  }

  static Timestamp ts(Instant value) {
    return Timestamp.from(value);
  }

  public void event(String id, String type, String detail) {
    db.update(
        "INSERT INTO audit_events(operation_id,occurred_at,type,detail) VALUES(?,?,?,?)",
        id,
        ts(Instant.now()),
        type,
        detail);
  }

  public Operation operation(String id) {
    var rows =
        db.query(
            "SELECT * FROM operations WHERE id=?",
            (rs, n) ->
                new Operation(
                    rs.getString("id"),
                    rs.getString("beneficiary"),
                    rs.getString("status"),
                    rs.getString("protocol"),
                    rs.getInt("attempts"),
                    rs.getTimestamp("next_attempt_at").toInstant(),
                    rs.getString("last_error"),
                    rs.getTimestamp("created_at").toInstant(),
                    json.read(rs.getString("payload"), OperationRequest.class),
                    List.of()),
            id);
    if (rows.isEmpty())
      throw new DomainException(
          DomainException.Kind.NOT_FOUND, "NAO_ENCONTRADA", "Operação não encontrada.");
    var op = rows.getFirst();
    var events =
        db.query(
            "SELECT * FROM audit_events WHERE operation_id=? ORDER BY sequence",
            (rs, n) ->
                new Event(
                    rs.getLong("sequence"),
                    rs.getTimestamp("occurred_at").toInstant(),
                    rs.getString("type"),
                    rs.getString("detail")),
            id);
    return new Operation(
        op.id(),
        op.beneficiarioId(),
        op.status(),
        op.protocolo(),
        op.tentativas(),
        op.proximaTentativa(),
        op.ultimoErro(),
        op.criadaEm(),
        op.solicitacao(),
        events);
  }

  public List<Operation> all() {
    return db
        .queryForList("SELECT id FROM operations ORDER BY created_at DESC", String.class)
        .stream()
        .map(this::operation)
        .toList();
  }

  public boolean authorized(String beneficiary) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM authorizations WHERE beneficiary=?", Integer.class, beneficiary)
        > 0;
  }

  public boolean agreementAuthorized(String beneficiary, String agreement) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM authorizations WHERE beneficiary=? AND agreement=?",
            Integer.class,
            beneficiary,
            agreement)
        > 0;
  }

  private void write(String sql, Object... args) {
    try {
      db.update(sql, args);
    } catch (DataIntegrityViolationException e) {
      throw new PersistenceConflict(e);
    }
  }

  public Optional<Authorization> authorization(String beneficiary, String agreement) {
    return db
        .query(
            "SELECT * FROM authorizations WHERE beneficiary=? AND agreement=?",
            (rs, n) -> new Authorization(rs.getString("id"), beneficiary, agreement),
            beneficiary,
            agreement)
        .stream()
        .findFirst();
  }

  public void saveAuthorization(Authorization auth) {
    write(
        "INSERT INTO authorizations(id,beneficiary,agreement,created_at) VALUES(?,?,?,?)",
        auth.id(),
        auth.beneficiarioId(),
        auth.convenioId(),
        ts(Instant.now()));
  }

  public Set<String> reservedInstallments(String beneficiary) {
    return new HashSet<>(
        db.queryForList(
            "SELECT installment_id FROM reservations WHERE beneficiary=?",
            String.class,
            beneficiary));
  }

  public Optional<Account> receivingAccount(String beneficiary, String installment) {
    return db
        .queryForList(
            "SELECT account_json FROM receiving_accounts WHERE beneficiary=? AND installment_id=?",
            String.class,
            beneficiary,
            installment)
        .stream()
        .findFirst()
        .map(value -> json.read(value, Account.class));
  }

  public Optional<Operation> findByKey(String key) {
    return db
        .queryForList("SELECT id FROM operations WHERE idempotency_key=?", String.class, key)
        .stream()
        .findFirst()
        .map(this::operation);
  }

  public void insertIntent(String id, String key, OperationRequest request, Instant now) {
    String payload = json.write(request);
    write(
        "INSERT INTO"
            + " operations(id,idempotency_key,payload_hash,payload,beneficiary,status,next_attempt_at,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,'RECEBIDA',?,?,?)",
        id,
        key,
        Hmac.hash(payload),
        payload,
        request.beneficiarioId(),
        ts(now),
        ts(now),
        ts(now));
  }

  public void reserve(String beneficiary, String installment, String operation) {
    write(
        "INSERT INTO reservations(beneficiary,installment_id,operation_id) VALUES(?,?,?)",
        beneficiary,
        installment,
        operation);
  }

  public boolean lock(String id) {
    return !db.queryForList("SELECT id FROM operations WHERE id=? FOR UPDATE", id).isEmpty();
  }

  public String idempotencyKey(String id) {
    return db.queryForObject("SELECT idempotency_key FROM operations WHERE id=?", String.class, id);
  }

  public List<String> dueIds(Instant now) {
    return db.queryForList(
        "SELECT id FROM operations WHERE status IN ('RECEBIDA','PENDENTE') AND next_attempt_at<=?"
            + " ORDER BY next_attempt_at LIMIT 20",
        String.class,
        ts(now));
  }

  public void accepted(String id, String protocol, Instant next, Instant now) {
    write(
        "UPDATE operations SET"
            + " status='PENDENTE',protocol=?,attempts=0,last_error=NULL,next_attempt_at=?,updated_at=?"
            + " WHERE id=?",
        protocol,
        ts(next),
        ts(now),
        id);
  }

  public void updateAccount(
      String beneficiary, String installment, Account account, String operation) {
    write(
        "INSERT INTO receiving_accounts(beneficiary,installment_id,account_json,operation_id)"
            + " VALUES(?,?,?,?)",
        beneficiary,
        installment,
        json.write(account),
        operation);
  }

  public void terminal(String id, String status, String reason, int attempt, Instant now) {
    write(
        "UPDATE operations SET status=?,last_error=?,attempts=?,updated_at=? WHERE id=?",
        status,
        reason,
        attempt,
        ts(now),
        id);
  }

  public void releaseReservations(String id) {
    write("DELETE FROM reservations WHERE operation_id=?", id);
  }

  public void review(String id, int attempt, String error, Instant now) {
    write(
        "UPDATE operations SET status='REVISAO_NECESSARIA',attempts=?,last_error=?,updated_at=?"
            + " WHERE id=?",
        attempt,
        error,
        ts(now),
        id);
  }

  public void retry(String id, int attempt, String error, Instant next, Instant now) {
    write(
        "UPDATE operations SET attempts=?,last_error=?,next_attempt_at=?,updated_at=? WHERE id=?",
        attempt,
        error,
        ts(next),
        ts(now),
        id);
  }

  public void resume(String id, String status, Instant now) {
    write(
        "UPDATE operations SET status=?,attempts=0,next_attempt_at=?,last_error=NULL,updated_at=?"
            + " WHERE id=?",
        status,
        ts(now),
        ts(now),
        id);
  }
}
