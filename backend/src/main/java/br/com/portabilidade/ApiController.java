package br.com.portabilidade;

import br.com.portabilidade.Models.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "app.role", havingValue = "api")
public class ApiController {
  private final PortabilityService service;
  private final Store store;

  public ApiController(PortabilityService service, Store store) {
    this.service = service;
    this.store = store;
  }

  @PostMapping("/autorizacoes")
  ResponseEntity<Authorization> authorize(@Valid @RequestBody AuthorizationRequest request) {
    return ResponseEntity.status(201).body(service.authorize(request));
  }

  @GetMapping("/beneficiarios/{id}/agenda")
  List<Installment> agenda(@PathVariable String id) {
    return service.agenda(id);
  }

  @PostMapping("/operacoes")
  ResponseEntity<Operation> create(
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody OperationRequest request) {
    var operation = service.create(key, request);
    return ResponseEntity.accepted()
        .location(URI.create("/api/operacoes/" + operation.id()))
        .body(operation);
  }

  @GetMapping("/operacoes")
  List<Operation> list() {
    return store.all();
  }

  @GetMapping("/operacoes/{id}")
  Operation get(@PathVariable String id) {
    return store.operation(id);
  }

  @PostMapping("/operacoes/{id}/retomar")
  ResponseEntity<Operation> resume(@PathVariable String id) {
    return ResponseEntity.accepted().body(service.resume(id));
  }

  @ExceptionHandler(CentralClient.IntegrationException.class)
  ResponseEntity<?> integration(CentralClient.IntegrationException ex) {
    return ResponseEntity.status(ex.retryable() ? 503 : 502)
        .body(java.util.Map.of("code", "FALHA_CENTRAL", "message", ex.getMessage()));
  }
}
