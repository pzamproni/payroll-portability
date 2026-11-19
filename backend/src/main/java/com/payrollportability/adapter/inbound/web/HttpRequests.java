package com.payrollportability.adapter.inbound.web;

import com.payrollportability.domain.Models;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Validation and transport mapping stay at the HTTP boundary, outside the domain. */
public final class HttpRequests {
  private HttpRequests() {}

  public record AuthorizationRequest(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}") String beneficiarioId,
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}") String convenioId,
      @NotNull @AssertTrue Boolean consentimento) {
    public Models.AuthorizationRequest toDomain() {
      return new Models.AuthorizationRequest(beneficiarioId, convenioId, consentimento);
    }
  }

  public record Account(
      @NotBlank @Pattern(regexp = "[0-9]{3}") String banco,
      @NotBlank @Pattern(regexp = "[0-9]{1,5}(-[0-9Xx])?") String agencia,
      @NotBlank @Pattern(regexp = "[0-9]{1,12}(-[0-9Xx])?") String conta) {
    public Models.Account toDomain() {
      return new Models.Account(banco, agencia, conta);
    }
  }

  public record OperationRequest(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}") String beneficiarioId,
      @NotEmpty @Size(max = 50)
          List<@NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}") String> parcelas,
      @NotBlank @Size(max = 80) String credorCessionario,
      @NotNull @Valid Account contaRecebimento,
      @NotNull Models.Scenario cenario) {
    public Models.OperationRequest toDomain() {
      return new Models.OperationRequest(
          beneficiarioId, parcelas, credorCessionario, contaRecebimento.toDomain(), cenario);
    }
  }
}
