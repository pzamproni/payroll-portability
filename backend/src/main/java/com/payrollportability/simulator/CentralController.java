package com.payrollportability.simulator;

import com.payrollportability.adapter.inbound.web.HttpRequests;
import com.payrollportability.domain.DomainException;
import com.payrollportability.domain.Models.Authorization;
import com.payrollportability.domain.Models.CentralStatus;
import com.payrollportability.domain.Models.Installment;
import com.payrollportability.domain.Models.Protocol;
import com.payrollportability.domain.Models.Scenario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.role", havingValue = "central")
public class CentralController {
  private final CentralService service;
  private final long timeout;

  public CentralController(CentralService service, @Value("${app.http-timeout-ms}") long timeout) {
    this.service = service;
    this.timeout = timeout;
  }

  @PostMapping("/autorizacoes")
  ResponseEntity<Authorization> authorize(
      @Valid @RequestBody HttpRequests.AuthorizationRequest request) {
    return ResponseEntity.status(201).body(service.authorize(request.toDomain()));
  }

  @GetMapping("/beneficiarios/{id}/agenda")
  List<Installment> agenda(@PathVariable String id) {
    return service.agenda(id);
  }

  @PostMapping("/operacoes-portabilidade")
  ResponseEntity<?> submit(
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody HttpRequests.OperationRequest transport,
      HttpServletRequest http) {
    var request = transport.toDomain();
    if (key == null || !key.matches("[a-zA-Z0-9_-]{8,100}"))
      throw new DomainException(
          DomainException.Kind.INVALID_REQUEST, "CHAVE_INVALIDA", "Chave idempotente obrigatória.");
    int attempt = service.attempt(key);
    if (attempt <= 2) {
      if (request.cenario() == Scenario.UNAVAILABLE)
        return ResponseEntity.status(503)
            .header("Retry-After", "1")
            .body(java.util.Map.of("erro", "Central temporariamente indisponível"));
      if (request.cenario() == Scenario.TIMEOUT) pause();
      if (request.cenario() == Scenario.INVALID_RESPONSE)
        return ResponseEntity.accepted().body("JSON propositalmente inválido");
      if (request.cenario() == Scenario.TAMPERED_RESPONSE) http.setAttribute("tamper", true);
    }
    String protocol = service.submit(key, request);
    if (request.cenario() == Scenario.TIMEOUT_AFTER_ACCEPTING && attempt == 1) pause();
    return ResponseEntity.accepted().body(new Protocol(protocol));
  }

  private void pause() {
    try {
      Thread.sleep(timeout + 800);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @GetMapping("/operacoes-portabilidade/{protocol}")
  ResponseEntity<CentralStatus> poll(@PathVariable String protocol) {
    var status = service.poll(protocol);
    return status.status().equals("PENDENTE")
        ? ResponseEntity.noContent().header("Retry-After", "2").build()
        : ResponseEntity.ok(status);
  }
}
