package com.payrollportability.simulator;

import com.payrollportability.infrastructure.json.Json;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "central")
public class SimulatorStore {
  final JdbcTemplate db;
  final Json json;

  public SimulatorStore(JdbcTemplate db, Json json) {
    this.db = db;
    this.json = json;
  }

  static Timestamp ts(Instant instant) {
    return Timestamp.from(instant);
  }

  boolean authorized(String beneficiary) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM authorizations WHERE beneficiary=?", Integer.class, beneficiary)
        > 0;
  }

  boolean agreementAuthorized(String beneficiary, String agreement) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM authorizations WHERE beneficiary=? AND agreement=?",
            Integer.class,
            beneficiary,
            agreement)
        > 0;
  }
}
