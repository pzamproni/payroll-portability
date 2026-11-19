package com.payrollportability.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class Models {
  private Models() {}

  public record AuthorizationRequest(
      String beneficiarioId, String convenioId, Boolean consentimento) {}

  public record Authorization(String id, String beneficiarioId, String convenioId) {}

  public record Account(String banco, String agencia, String conta) {}

  public enum Scenario {
    NORMAL,
    REJECT,
    UNAVAILABLE,
    TIMEOUT,
    TIMEOUT_AFTER_ACCEPTING,
    INVALID_RESPONSE,
    TAMPERED_RESPONSE
  }

  public record OperationRequest(
      String beneficiarioId,
      List<String> parcelas,
      String credorCessionario,
      Account contaRecebimento,
      Scenario cenario) {
    public OperationRequest {
      parcelas = List.copyOf(parcelas);
    }

    public OperationRequest normalized() {
      if (parcelas.stream().distinct().count() != parcelas.size())
        throw new DomainException(
            DomainException.Kind.INVALID_REQUEST,
            "PARCELAS_DUPLICADAS",
            "Selecione cada parcela apenas uma vez.");
      return new OperationRequest(
          beneficiarioId,
          parcelas.stream().sorted().toList(),
          credorCessionario.strip(),
          contaRecebimento,
          cenario);
    }
  }

  public record Installment(
      String parcelaId,
      String convenioId,
      BigDecimal valor,
      LocalDate dataDesconto,
      String status,
      boolean elegivel,
      Account contaRecebimento) {}

  public record Event(long sequence, Instant quando, String tipo, String detalhe) {}

  public record Operation(
      String id,
      String beneficiarioId,
      String status,
      String protocolo,
      int tentativas,
      Instant proximaTentativa,
      String ultimoErro,
      Instant criadaEm,
      OperationRequest solicitacao,
      List<Event> eventos) {}

  public record Protocol(String protocolo) {}

  public record CentralStatus(String status, String motivoRejeicao) {}
}
