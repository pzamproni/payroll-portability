package com.payrollportability.adapter.outbound.central;

import com.payrollportability.application.port.outbound.CentralGateway;
import com.payrollportability.domain.Models.Authorization;
import com.payrollportability.domain.Models.AuthorizationRequest;
import com.payrollportability.domain.Models.CentralStatus;
import com.payrollportability.domain.Models.Installment;
import com.payrollportability.domain.Models.OperationRequest;
import com.payrollportability.domain.Models.Protocol;
import com.payrollportability.infrastructure.json.Json;
import com.payrollportability.infrastructure.security.Hmac;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "api")
public class CentralClient implements CentralGateway {
  private final HttpClient client;
  private final String base;
  private final Duration timeout;
  private final Hmac hmac;
  private final Json json;

  public CentralClient(
      Hmac hmac,
      Json json,
      @Value("${app.central-url}") String base,
      @Value("${app.http-timeout-ms}") long timeout) {
    this.hmac = hmac;
    this.json = json;
    this.base = base;
    this.timeout = Duration.ofMillis(timeout);
    client =
        HttpClient.newBuilder()
            .connectTimeout(this.timeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  private HttpResponse<String> exchange(
      String method, String path, Object data, String key, int... expected) {
    String body = data == null ? "" : json.write(data),
        ts = Long.toString(Instant.now().getEpochSecond()),
        nonce = UUID.randomUUID().toString();
    var request =
        HttpRequest.newBuilder(URI.create(base + path))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("X-Timestamp", ts)
            .header("X-Nonce", nonce)
            .header("X-Signature", hmac.sign(hmac.request(method, path, ts, nonce, key, body)))
            .method(
                method,
                body.isEmpty()
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
    if (key != null) request.header("Idempotency-Key", key);
    try {
      var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
      String responseTs = response.headers().firstValue("X-Timestamp").orElse("");
      String signature = response.headers().firstValue("X-Signature").orElse("");
      if (!hmac.verify(
          hmac.response(response.statusCode(), responseTs, nonce, response.body()),
          signature,
          responseTs))
        throw new IntegrationException(
            "Integridade da resposta da Central inválida (HMAC).", true, 0);
      long retryAfter =
          response
              .headers()
              .firstValue("Retry-After")
              .map(
                  v -> {
                    try {
                      return Math.min(300, Math.max(0, Long.parseLong(v))) * 1000;
                    } catch (NumberFormatException e) {
                      return 0L;
                    }
                  })
              .orElse(0L);
      if (Arrays.stream(expected).noneMatch(s -> s == response.statusCode())) {
        int code = response.statusCode();
        throw new IntegrationException(
            "Central retornou HTTP " + code + (code == 503 ? " (indisponível)." : "."),
            code == 429 || code >= 500,
            retryAfter);
      }
      return response;
    } catch (HttpTimeoutException e) {
      throw new IntegrationException(
          "Timeout ao comunicar com a Central; resultado remoto pode estar pendente.", true, 0);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IntegrationException("Comunicação interrompida.", true, 0);
    } catch (java.io.IOException e) {
      throw new IntegrationException("Central indisponível: falha de conexão.", true, 0);
    }
  }

  private <T> T parse(String body, Class<T> type) {
    try {
      T result = json.read(body, type);
      if (result == null) throw new IllegalArgumentException("Corpo nulo");
      return result;
    } catch (IllegalArgumentException e) {
      throw new IntegrationException("Resposta inesperada da Central: JSON inválido.", true, 0);
    }
  }

  public Authorization authorize(AuthorizationRequest request) {
    var auth =
        parse(exchange("POST", "/autorizacoes", request, null, 201).body(), Authorization.class);
    if (!validText(auth.id(), 36)
        || !request.beneficiarioId().equals(auth.beneficiarioId())
        || !request.convenioId().equals(auth.convenioId()))
      throw new IntegrationException("Autorização inesperada da Central.", true, 0);
    return auth;
  }

  public List<Installment> agenda(String beneficiary) {
    Installment[] result =
        parse(
            exchange("GET", "/beneficiarios/" + beneficiary + "/agenda", null, null, 200).body(),
            Installment[].class);
    var identifiers = new HashSet<String>();
    for (Installment item : result) {
      if (item == null
          || !validText(item.parcelaId(), 80)
          || !validText(item.convenioId(), 80)
          || item.valor() == null
          || item.valor().signum() <= 0
          || item.dataDesconto() == null
          || item.status() == null
          || !Set.of("ABERTA", "PAGA", "BLOQUEADA", "PORTADA").contains(item.status())
          || !identifiers.add(item.parcelaId())) {
        throw new IntegrationException("Agenda inesperada da Central.", true, 0);
      }
    }
    return List.of(result);
  }

  public String submit(OperationRequest request, String key) {
    var result =
        parse(
            exchange("POST", "/operacoes-portabilidade", request, key, 202).body(), Protocol.class);
    try {
      UUID.fromString(result.protocolo());
    } catch (Exception e) {
      throw new IntegrationException("Protocolo inválido na resposta da Central.", true, 0);
    }
    return result.protocolo();
  }

  public Poll poll(String protocol) {
    var response = exchange("GET", "/operacoes-portabilidade/" + protocol, null, null, 200, 204);
    long delay =
        response
            .headers()
            .firstValue("Retry-After")
            .map(
                v -> {
                  try {
                    return Math.min(300, Math.max(0, Long.parseLong(v))) * 1000;
                  } catch (NumberFormatException e) {
                    return 0L;
                  }
                })
            .orElse(0L);
    if (response.statusCode() == 204) return new Poll(new CentralStatus("PENDENTE", null), delay);
    var status = parse(response.body(), CentralStatus.class);
    if (status == null
        || status.status() == null
        || !Set.of("PENDENTE", "CONFIRMADA", "REJEITADA").contains(status.status()))
      throw new IntegrationException("Status inesperado da Central.", true, 0);
    if (status.motivoRejeicao() != null && status.motivoRejeicao().length() > 1000)
      throw new IntegrationException(
          "Motivo de rejeição inválido na resposta da Central.", true, 0);
    return new Poll(status, delay);
  }

  private boolean validText(String value, int maxLength) {
    return value != null && !value.isBlank() && value.length() <= maxLength;
  }
}
