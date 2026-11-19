package br.com.portabilidade;

import br.com.portabilidade.Models.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Store {
  final JdbcTemplate db;
  final Json json;

  public Store(JdbcTemplate db, Json json) {
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
      throw new DomainException(404, "NAO_ENCONTRADA", "Operação não encontrada.");
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
}
