package br.com.portabilidade;

import static org.assertj.core.api.Assertions.*;

import br.com.portabilidade.Models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.*;

class CentralClientContractTest {
  HttpServer server;
  CentralClient client;
  String body;
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
              .set("X-Signature", hmac.sign(hmac.response(200, timestamp, nonce, body)));
          exchange.sendResponseHeaders(200, bytes.length);
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
        .isInstanceOf(CentralClient.IntegrationException.class)
        .hasMessageContaining("JSON inválido");
  }

  @Test
  void signedUnknownStatusIsAnExplicitIntegrationFailure() {
    body = "{}";
    assertThatThrownBy(() -> client.poll("protocol"))
        .isInstanceOf(CentralClient.IntegrationException.class)
        .hasMessageContaining("Status inesperado");
  }
}
