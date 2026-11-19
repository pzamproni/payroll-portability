package com.payrollportability;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payrollportability.adapter.outbound.central.CentralClient;
import com.payrollportability.application.port.outbound.CentralGateway;
import com.payrollportability.domain.Models.AuthorizationRequest;
import com.payrollportability.infrastructure.json.Json;
import com.payrollportability.infrastructure.security.Hmac;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CentralClientContractTest {
  HttpServer server;
  CentralClient client;
  String body;
  int responseStatus = 200;
  final Hmac hmac = new Hmac("test-secret-with-at-least-32-characters");

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String timestamp = Long.toString(Instant.now().getEpochSecond());
          String nonce = exchange.getRequestHeaders().getFirst("X-Nonce");
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("X-Timestamp", timestamp);
          exchange
              .getResponseHeaders()
              .set("X-Signature", hmac.sign(hmac.response(responseStatus, timestamp, nonce, body)));
          exchange.sendResponseHeaders(responseStatus, bytes.length);
          try (var out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    server.start();
    client =
        new CentralClient(
            hmac,
            new Json(new ObjectMapper().findAndRegisterModules()),
            "http://127.0.0.1:" + server.getAddress().getPort(),
            1000);
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void signedNullIsAnExplicitIntegrationFailure() {
    body = "null";
    assertThatThrownBy(() -> client.poll("protocol"))
        .isInstanceOf(CentralGateway.IntegrationException.class)
        .hasMessageContaining("JSON inválido");
  }

  @Test
  void signedUnknownStatusIsAnExplicitIntegrationFailure() {
    body = "{}";
    assertThatThrownBy(() -> client.poll("protocol"))
        .isInstanceOf(CentralGateway.IntegrationException.class)
        .hasMessageContaining("Status inesperado");
  }

  private String installment(String id, String amount, String status) {
    return "{\"parcelaId\":\""
        + id
        + "\",\"convenioId\":\"CONV\",\"valor\":"
        + amount
        + ",\"dataDesconto\":\"2030-01-01\",\"status\":\""
        + status
        + "\",\"elegivel\":true,\"contaRecebimento\":null}";
  }

  @Test
  void nullAgendaEntryIsExplicitIntegrationFailure() {
    body = "[null]";
    assertThatThrownBy(() -> client.agenda("BEN"))
        .isInstanceOf(CentralGateway.IntegrationException.class)
        .hasMessageContaining("Agenda inesperada");
  }

  @Test
  void duplicateAgendaIdsAreRejected() {
    String entry = installment("p1", "100.00", "ABERTA");
    body = "[" + entry + "," + entry + "]";
    assertThatThrownBy(() -> client.agenda("BEN"))
        .isInstanceOf(CentralGateway.IntegrationException.class)
        .hasMessageContaining("Agenda inesperada");
  }

  @Test
  void invalidAgendaValuesFailExplicitly() {
    for (String entry :
        java.util.List.of(
            installment("p1", "0", "ABERTA"),
            installment("p1", "-1", "ABERTA"),
            installment("p1", "100", "DESCONHECIDA"))) {
      body = "[" + entry + "]";
      assertThatThrownBy(() -> client.agenda("BEN"))
          .isInstanceOf(CentralGateway.IntegrationException.class)
          .hasMessageContaining("Agenda inesperada");
    }
  }

  @Test
  void oversizedRejectionReasonIsRejected() {
    body = "{\"status\":\"REJEITADA\",\"motivoRejeicao\":\"" + "x".repeat(1001) + "\"}";
    assertThatThrownBy(() -> client.poll("protocol"))
        .isInstanceOf(CentralGateway.IntegrationException.class)
        .hasMessageContaining("Motivo de rejeição");
  }

  @Test
  void invalidAuthorizationIdentifierIsRejected() {
    responseStatus = 201;
    for (String id : java.util.List.of("", "x".repeat(37))) {
      body = "{\"id\":\"" + id + "\",\"beneficiarioId\":\"BEN\",\"convenioId\":\"CONV\"}";
      assertThatThrownBy(() -> client.authorize(new AuthorizationRequest("BEN", "CONV", true)))
          .isInstanceOf(CentralGateway.IntegrationException.class)
          .hasMessageContaining("Autorização inesperada");
    }
  }
}
