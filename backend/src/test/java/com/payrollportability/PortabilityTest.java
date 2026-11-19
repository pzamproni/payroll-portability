package com.payrollportability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.payrollportability.application.port.outbound.CentralGateway;
import com.payrollportability.application.port.outbound.OperationRepository;
import com.payrollportability.application.port.outbound.UnitOfWork;
import com.payrollportability.application.service.OperationProcessor;
import com.payrollportability.application.service.PortabilityService;
import com.payrollportability.application.service.RetryPolicy;
import com.payrollportability.domain.DomainException;
import com.payrollportability.domain.Models.Account;
import com.payrollportability.domain.Models.CentralStatus;
import com.payrollportability.domain.Models.Event;
import com.payrollportability.domain.Models.Installment;
import com.payrollportability.domain.Models.Operation;
import com.payrollportability.domain.Models.OperationRequest;
import com.payrollportability.domain.Models.Scenario;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "app.role=api",
      "spring.datasource.url=jdbc:h2:mem:api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.locations=classpath:db/migration",
      "app.hmac-secret=test-secret-with-at-least-32-characters",
      "app.worker-delay-ms=86400000",
      "app.retry-base-ms=5",
      "app.retry-max-ms=50",
      "app.max-attempts=3"
    })
class PortabilityTest {
  @Autowired PortabilityService service;
  @Autowired JdbcTemplate db;
  @Autowired OperationRepository store;
  @Autowired UnitOfWork unitOfWork;
  @Autowired OperationProcessor worker;
  @MockitoBean CentralGateway central;
  final Account destination = new Account("001", "1234", "12345-6");

  @BeforeEach
  void setup() {
    for (String table :
        List.of(
            "audit_events", "receiving_accounts", "reservations", "operations", "authorizations"))
      db.update("DELETE FROM " + table);
    reset(central);
    db.update(
        "INSERT INTO authorizations(id,beneficiary,agreement,created_at)"
            + " VALUES('auth','BEN','CONV',?)",
        java.sql.Timestamp.from(Instant.now()));
    when(central.agenda("BEN"))
        .thenReturn(
            List.of(
                new Installment(
                    "p1",
                    "CONV",
                    new BigDecimal("100.01"),
                    LocalDate.now(ZoneOffset.UTC).plusDays(10),
                    "ABERTA",
                    true,
                    null),
                new Installment(
                    "p2",
                    "CONV",
                    new BigDecimal("200.02"),
                    LocalDate.now(ZoneOffset.UTC).plusDays(20),
                    "PAGA",
                    false,
                    null)));
  }

  OperationRequest request() {
    return new OperationRequest("BEN", List.of("p1"), "Credor", destination, Scenario.NORMAL);
  }

  Operation create() {
    return service.create("idempotency-1", request());
  }

  void due(String id) {
    db.update(
        "UPDATE operations SET next_attempt_at=? WHERE id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        id);
    worker.process(id);
  }

  void accept(String id) {
    when(central.submit(any(), anyString())).thenReturn("protocol-1");
    due(id);
  }

  @Test
  void requiresOptIn() {
    assertThatThrownBy(() -> service.agenda("UNKNOWN"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("opt-in");
    verifyNoInteractions(central);
  }

  @Test
  void rejectsMissingKey() {
    assertThatThrownBy(() -> service.create(null, request())).isInstanceOf(DomainException.class);
  }

  @Test
  void persistsIntentAndAuditBeforeSending() {
    var op = create();
    assertThat(op.status()).isEqualTo("RECEBIDA");
    assertThat(op.eventos()).extracting(Event::tipo).containsExactly("SOLICITADA");
    verify(central, never()).submit(any(), any());
  }

  @Test
  void sameKeySameRequestReturnsSameOperation() {
    var one = create();
    assertThat(create().id()).isEqualTo(one.id());
    assertThat(store.all()).hasSize(1);
  }

  @Test
  void changedPayloadSameKeyIsConflict() {
    create();
    var changed = new OperationRequest("BEN", List.of("p1"), "Outro", destination, Scenario.NORMAL);
    assertThatThrownBy(() -> service.create("idempotency-1", changed))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("outro conteúdo");
  }

  @Test
  void reservesAgainstDifferentKeys() {
    create();
    assertThatThrownBy(() -> service.create("idempotency-2", request()))
        .isInstanceOf(DomainException.class);
    assertThat(service.agenda("BEN").getFirst().elegivel()).isFalse();
  }

  @Test
  void rejectsPaidInstallmentAndDuplicateSelection() {
    assertThatThrownBy(
            () ->
                service.create(
                    "idempotency-1",
                    new OperationRequest(
                        "BEN", List.of("p2"), "Credor", destination, Scenario.NORMAL)))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () ->
                new OperationRequest(
                        "BEN", List.of("p1", "p1"), "Credor", destination, Scenario.NORMAL)
                    .normalized())
        .isInstanceOf(DomainException.class);
  }

  @Test
  void concurrentSameKeyCreatesOneOperation() throws Exception {
    try (var pool = Executors.newFixedThreadPool(6)) {
      var start = new CountDownLatch(1);
      var futures = new ArrayList<Future<String>>();
      for (int i = 0; i < 6; i++)
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return create().id();
                }));
      start.countDown();
      var ids = new HashSet<String>();
      for (var future : futures) ids.add(future.get(10, TimeUnit.SECONDS));
      assertThat(ids).hasSize(1);
      assertThat(store.all()).hasSize(1);
    }
  }

  @Test
  void confirmationAtomicallyUpdatesAccountAndAudit() {
    var op = create();
    accept(op.id());
    assertThat(db.queryForObject("SELECT COUNT(*) FROM receiving_accounts", Integer.class))
        .isZero();
    when(central.poll("protocol-1"))
        .thenReturn(new CentralGateway.Poll(new CentralStatus("CONFIRMADA", null), 0));
    due(op.id());
    var result = store.operation(op.id());
    assertThat(result.status()).isEqualTo("CONFIRMADA");
    assertThat(result.eventos())
        .extracting(Event::tipo)
        .containsExactly("SOLICITADA", "ACEITA_CENTRAL", "CONFIRMADA", "CONTA_ATUALIZADA");
    assertThat(service.agenda("BEN").getFirst().contaRecebimento()).isEqualTo(destination);
    due(op.id());
    verify(central, times(1)).poll(any());
  }

  @Test
  void rejectionPreservesAccountAndReleasesReservation() {
    var op = create();
    accept(op.id());
    when(central.poll(any()))
        .thenReturn(
            new CentralGateway.Poll(new CentralStatus("REJEITADA", "Política do convênio"), 0));
    due(op.id());
    assertThat(store.operation(op.id()).status()).isEqualTo("REJEITADA");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM receiving_accounts", Integer.class))
        .isZero();
    assertThat(service.agenda("BEN").getFirst().elegivel()).isTrue();
  }

  @Test
  void temporaryFailureSchedulesRetryWithOriginalKey() {
    var op = create();
    when(central.submit(any(), any()))
        .thenThrow(new CentralGateway.IntegrationException("Central indisponível", true, 1000));
    due(op.id());
    var result = store.operation(op.id());
    assertThat(result.status()).isEqualTo("RECEBIDA");
    assertThat(result.ultimoErro()).contains("indisponível");
    assertThat(result.proximaTentativa()).isAfter(Instant.now().plusMillis(800));
    doReturn("protocol-1").when(central).submit(any(), any());
    due(op.id());
    verify(central, times(2)).submit(request(), "idempotency-1");
    assertThat(store.operation(op.id()).status()).isEqualTo("PENDENTE");
  }

  @Test
  void exhaustedRetriesRequireReviewAndResumePreservesProtocol() {
    var op = create();
    accept(op.id());
    when(central.poll(any()))
        .thenThrow(new CentralGateway.IntegrationException("Timeout", true, 0));
    for (int i = 0; i < 3; i++) due(op.id());
    assertThat(store.operation(op.id()).status()).isEqualTo("REVISAO_NECESSARIA");
    service.resume(op.id());
    assertThat(store.operation(op.id()).protocolo()).isEqualTo("protocol-1");
    assertThat(store.operation(op.id()).status()).isEqualTo("PENDENTE");
    assertThat(store.operation(op.id()).eventos())
        .extracting(Event::tipo)
        .contains("REVISAO_NECESSARIA", "RETOMADA");
  }

  @Test
  void nonRetryableFailureGoesToReviewWithoutRejectingBusiness() {
    var op = create();
    when(central.submit(any(), any()))
        .thenThrow(new CentralGateway.IntegrationException("HTTP 409", false, 0));
    due(op.id());
    assertThat(store.operation(op.id()).status()).isEqualTo("REVISAO_NECESSARIA");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class)).isEqualTo(1);
  }

  @Test
  void resumeCannotChangeTerminalState() {
    var op = create();
    assertThatThrownBy(() -> service.resume(op.id())).isInstanceOf(DomainException.class);
  }

  @Test
  void exponentialBackoffIsCappedAndRespectsRetryAfter() {
    assertThat(worker.backoff(1, 0)).isBetween(5L, 6L);
    assertThat(worker.backoff(30, 0)).isEqualTo(50);
    assertThat(worker.backoff(1, 2000)).isEqualTo(2000);
  }

  @Test
  void newWorkerInstanceRecoversPersistedIntent() {
    var op = create();
    when(central.submit(any(), any())).thenReturn("protocol-recovered");
    var recovered = new OperationProcessor(store, central, unitOfWork, new RetryPolicy(5, 50, 3));
    recovered.process(op.id());
    assertThat(store.operation(op.id()).protocolo()).isEqualTo("protocol-recovered");
  }

  @Test
  void concurrentDifferentKeysReserveOnlyOnce() throws Exception {
    try (var pool = Executors.newFixedThreadPool(4)) {
      var start = new CountDownLatch(1);
      var results = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 4; i++) {
        String key = "concurrent-" + i;
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    service.create(key, request());
                    return true;
                  } catch (DomainException e) {
                    assertThat(e.kind()).isEqualTo(DomainException.Kind.CONFLICT);
                    return false;
                  }
                }));
      }
      start.countDown();
      int accepted = 0;
      for (var result : results) if (result.get(10, TimeUnit.SECONDS)) accepted++;
      assertThat(accepted).isEqualTo(1);
      assertThat(store.all()).hasSize(1);
    }
  }
}
