package com.payrollportability.adapter.inbound.web;

import com.payrollportability.application.port.inbound.PortabilityUseCases;
import com.payrollportability.application.port.outbound.CentralGateway;
import com.payrollportability.domain.Models.Authorization;
import com.payrollportability.domain.Models.Installment;
import com.payrollportability.domain.Models.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "app.role", havingValue = "api")
public class ApiController {
  private final PortabilityUseCases service;

  public ApiController(PortabilityUseCases service) {
    this.service = service;
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

  @PostMapping("/operacoes")
  ResponseEntity<Operation> create(
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody HttpRequests.OperationRequest request) {
    var operation = service.create(key, request.toDomain());
    return ResponseEntity.accepted()
        .location(URI.create("/api/operacoes/" + operation.id()))
        .body(operation);
  }

  @GetMapping("/operacoes")
  List<Operation> list() {
    return service.list();
  }

  @GetMapping("/operacoes/{id}")
  Operation get(@PathVariable String id) {
    return service.get(id);
  }

  @PostMapping("/operacoes/{id}/retomar")
  ResponseEntity<Operation> resume(@PathVariable String id) {
    return ResponseEntity.accepted().body(service.resume(id));
  }

  @ExceptionHandler(CentralGateway.IntegrationException.class)
  ResponseEntity<?> integration(CentralGateway.IntegrationException ex) {
    return ResponseEntity.status(ex.retryable() ? 503 : 502)
        .body(java.util.Map.of("code", "FALHA_CENTRAL", "message", ex.getMessage()));
  }
}
