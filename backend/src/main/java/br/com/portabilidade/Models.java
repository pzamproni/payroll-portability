package br.com.portabilidade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class Models {
  private Models() {}

  public record AuthorizationRequest(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}") String beneficiarioId,
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}") String convenioId,
      @NotNull @AssertTrue Boolean consentimento) {}

  public record Authorization(String id, String beneficiarioId, String convenioId) {}

  public record Account(
      @NotBlank @Pattern(regexp = "[0-9]{3}") String banco,
      @NotBlank @Pattern(regexp = "[0-9]{1,5}(-[0-9Xx])?") String agencia,
      @NotBlank @Pattern(regexp = "[0-9]{1,12}(-[0-9Xx])?") String conta) {}

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
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}") String beneficiarioId,
      @NotEmpty @Size(max = 50)
          List<@NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}") String> parcelas,
      @NotBlank @Size(max = 80) String credorCessionario,
      @NotNull @Valid Account contaRecebimento,
      @NotNull Scenario cenario) {
    public OperationRequest normalized() {
      if (parcelas.stream().distinct().count() != parcelas.size())
        throw new DomainException(
            400, "PARCELAS_DUPLICADAS", "Selecione cada parcela apenas uma vez.");
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
