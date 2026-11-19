package br.com.portabilidade;

import static org.assertj.core.api.Assertions.*;

import br.com.portabilidade.Models.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.role=central",
      "spring.datasource.url=jdbc:h2:mem:central;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.locations=classpath:db/migration",
      "app.hmac-secret=test-secret-with-at-least-32-characters",
      "app.confirmation-delay-ms=0"
    })
class CentralHttpTest {
  @LocalServerPort int port;
  @Autowired Hmac hmac;
  @Autowired Json json;
  @Autowired CentralService service;
  @Autowired Store store;
  CentralClient client;

  @BeforeEach
  void setup() {
    client = new CentralClient(hmac, json, "http://localhost:" + port, 2000);
  }

  OperationRequest request(String beneficiary, Scenario scenario) {
    client.authorize(new AuthorizationRequest(beneficiary, "CONV", true));
    var p = client.agenda(beneficiary).getFirst();
    return new OperationRequest(
        beneficiary, List.of(p.parcelaId()), "Credor", new Account("001", "1", "2-3"), scenario);
  }

  @Test
  void realHttpRoundtripSignsBothDirectionsAndIsIdempotent() {
    var req = request("HTTP-OK", Scenario.NORMAL);
    String key = UUID.randomUUID().toString(), protocol = client.submit(req, key);
    assertThat(client.submit(req, key)).isEqualTo(protocol);
    assertThat(client.poll(protocol).result().status()).isEqualTo("CONFIRMADA");
    assertThat(client.agenda("HTTP-OK").getFirst().contaRecebimento())
        .isEqualTo(req.contaRecebimento());
  }

  @Test
  void realHttpRejectionKeepsOriginalAccount() {
    var req = request("HTTP-REJECT", Scenario.REJECT);
    var protocol = client.submit(req, UUID.randomUUID().toString());
    assertThat(client.poll(protocol).result().status()).isEqualTo("REJEITADA");
    assertThat(client.agenda("HTTP-REJECT").getFirst().contaRecebimento()).isNull();
  }

  @Test
  void detectsMalformedResponse() {
    var req = request("HTTP-INVALID", Scenario.INVALID_RESPONSE);
    assertThatThrownBy(() -> client.submit(req, UUID.randomUUID().toString()))
        .isInstanceOf(CentralClient.IntegrationException.class)
        .hasMessageContaining("JSON inválido");
  }

  @Test
  void detectsTamperedResponseWithoutDuplicatingRemoteOperation() {
    var req = request("HTTP-TAMPER", Scenario.TAMPERED_RESPONSE);
    String key = UUID.randomUUID().toString();
    for (int i = 0; i < 2; i++)
      assertThatThrownBy(() -> client.submit(req, key)).hasMessageContaining("HMAC");
    String protocol = client.submit(req, key);
    assertThat(
            store.db.queryForObject(
                "SELECT COUNT(*) FROM central_operations WHERE idempotency_key=?",
                Integer.class,
                key))
        .isEqualTo(1);
    assertThat(protocol).isNotBlank();
  }

  @Test
  void handles503AndRetryAfter() {
    var req = request("HTTP-503", Scenario.UNAVAILABLE);
    try {
      client.submit(req, UUID.randomUUID().toString());
      fail("Expected failure");
    } catch (CentralClient.IntegrationException e) {
      assertThat(e.retryable()).isTrue();
      assertThat(e.retryAfterMs()).isEqualTo(1000);
    }
  }

  @Test
  void unsignedRequestIsRejected() throws Exception {
    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/beneficiarios/BEN/agenda"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void mismatchedPayloadSameRemoteKeyIsConflict() {
    var req = request("HTTP-CONFLICT", Scenario.NORMAL);
    var key = UUID.randomUUID().toString();
    client.submit(req, key);
    assertThatThrownBy(
            () ->
                client.submit(
                    new OperationRequest(
                        req.beneficiarioId(),
                        req.parcelas(),
                        "Outro",
                        req.contaRecebimento(),
                        req.cenario()),
                    key))
        .hasMessageContaining("409");
  }

  @Test
  void remoteAuthorizationIsUnique() {
    var req = new AuthorizationRequest("HTTP-AUTH", "CONV", true);
    assertThat(client.authorize(req).id()).isEqualTo(client.authorize(req).id());
  }

  @Test
  void pendingUses204AndRetryAfter() {
    var req = request("HTTP-PENDING", Scenario.NORMAL);
    String protocol = client.submit(req, UUID.randomUUID().toString());
    store.db.update(
        "UPDATE central_operations SET confirm_at=? WHERE protocol=?",
        Store.ts(Instant.now().plusSeconds(10)),
        protocol);
    var poll = client.poll(protocol);
    assertThat(poll.result().status()).isEqualTo("PENDENTE");
    assertThat(poll.retryAfterMs()).isEqualTo(2000);
  }

  @Test
  void timeoutAfterRemoteAcceptanceReusesSameProtocol() {
    var req = request("HTTP-TIMEOUT", Scenario.TIMEOUT_AFTER_ACCEPTING);
    String key = UUID.randomUUID().toString();
    var impatient = new CentralClient(hmac, json, "http://localhost:" + port, 150);
    assertThatThrownBy(() -> impatient.submit(req, key)).hasMessageContaining("Timeout");
    String protocol = client.submit(req, key);
    assertThat(
            store.db.queryForObject(
                "SELECT COUNT(*) FROM central_operations WHERE idempotency_key=?",
                Integer.class,
                key))
        .isEqualTo(1);
    assertThat(client.submit(req, key)).isEqualTo(protocol);
  }

  @Test
  void agendaIncludesAmountsDatesAgreementAndEligibility() {
    client.authorize(new AuthorizationRequest("HTTP-AGENDA", "CONV", true));
    var agenda = client.agenda("HTTP-AGENDA");
    assertThat(agenda).hasSize(6);
    assertThat(agenda)
        .allSatisfy(
            p -> {
              assertThat(p.valor()).isPositive();
              assertThat(p.dataDesconto())
                  .isAfter(java.time.LocalDate.now(java.time.ZoneOffset.UTC));
              assertThat(p.convenioId()).isEqualTo("CONV");
            });
    assertThat(agenda.stream().filter(p -> p.status().equals("ABERTA")).count()).isEqualTo(4);
    assertThat(agenda.stream().filter(p -> p.status().equals("PAGA")).count()).isEqualTo(1);
    assertThat(agenda.stream().filter(p -> p.status().equals("BLOQUEADA")).count()).isEqualTo(1);
  }
}
