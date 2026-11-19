package br.com.portabilidade;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  private static final Logger log = LoggerFactory.getLogger(Errors.class);

  @ExceptionHandler(DomainException.class)
  ResponseEntity<?> domain(DomainException ex) {
    return ResponseEntity.status(ex.status())
        .body(Map.of("code", ex.code(), "message", ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
    String fields =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField())
            .distinct()
            .sorted()
            .reduce((a, b) -> a + ", " + b)
            .orElse("requisição");
    return ResponseEntity.badRequest()
        .body(Map.of("code", "VALIDACAO", "message", "Verifique os campos: " + fields));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<?> malformed() {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "code",
                "JSON_INVALIDO",
                "message",
                "Corpo JSON inválido ou cenário desconhecido."));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict() {
    return ResponseEntity.status(409)
        .body(
            Map.of(
                "code",
                "CONFLITO",
                "message",
                "Solicitação concorrente: consulte a operação ou atualize a agenda."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception ex) {
    log.error("Falha não prevista: {}", ex.getClass().getSimpleName());
    return ResponseEntity.internalServerError()
        .body(
            Map.of("code", "ERRO_INTERNO", "message", "Não foi possível concluir a solicitação."));
  }
}
