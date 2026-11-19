package com.payrollportability.application.service;

import com.payrollportability.application.port.inbound.PortabilityUseCases;
import com.payrollportability.application.port.outbound.AgendaStateRepository;
import com.payrollportability.application.port.outbound.AuthorizationRepository;
import com.payrollportability.application.port.outbound.CentralGateway;
import com.payrollportability.application.port.outbound.OperationRepository;
import com.payrollportability.application.port.outbound.UnitOfWork;
import com.payrollportability.domain.DomainException;
import com.payrollportability.domain.Models.Account;
import com.payrollportability.domain.Models.Authorization;
import com.payrollportability.domain.Models.AuthorizationRequest;
import com.payrollportability.domain.Models.Installment;
import com.payrollportability.domain.Models.Operation;
import com.payrollportability.domain.Models.OperationRequest;
import com.payrollportability.domain.PersistenceConflict;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Business orchestration. Infrastructure is supplied exclusively through outbound ports. */
public class PortabilityService implements PortabilityUseCases {
  private final AuthorizationRepository authorizations;
  private final AgendaStateRepository agendaState;
  private final OperationRepository operations;
  private final CentralGateway central;
  private final UnitOfWork tx;

  public PortabilityService(
      AuthorizationRepository authorizations,
      AgendaStateRepository agendaState,
      OperationRepository operations,
      CentralGateway central,
      UnitOfWork tx) {
    this.authorizations = authorizations;
    this.agendaState = agendaState;
    this.operations = operations;
    this.central = central;
    this.tx = tx;
  }

  public Authorization authorize(AuthorizationRequest request) {
    if (!Boolean.TRUE.equals(request.consentimento()))
      throw new DomainException(
          DomainException.Kind.INVALID_REQUEST,
          "CONSENTIMENTO_OBRIGATORIO",
          "Consentimento explícito obrigatório.");
    var existing = authorizations.authorization(request.beneficiarioId(), request.convenioId());
    if (existing.isPresent()) return existing.get();
    var auth = central.authorize(request);
    try {
      tx.execute(() -> authorizations.saveAuthorization(auth));
    } catch (PersistenceConflict e) {
      return authorizations
          .authorization(request.beneficiarioId(), request.convenioId())
          .orElseThrow(() -> e);
    }
    return auth;
  }

  public List<Installment> agenda(String beneficiary) {
    if (!beneficiary.matches("[a-zA-Z0-9_-]{1,80}"))
      throw new DomainException(
          DomainException.Kind.INVALID_REQUEST, "BENEFICIARIO_INVALIDO", "Identificador inválido.");
    if (!authorizations.authorized(beneficiary))
      throw new DomainException(
          DomainException.Kind.UNAUTHORIZED,
          "SEM_AUTORIZACAO",
          "Registre o opt-in antes de consultar a agenda.");
    var remote = central.agenda(beneficiary);
    var reserved = agendaState.reservedInstallments(beneficiary);
    return remote.stream()
        .filter(p -> authorizations.agreementAuthorized(beneficiary, p.convenioId()))
        .map(
            p -> {
              Account account =
                  agendaState
                      .receivingAccount(beneficiary, p.parcelaId())
                      .orElse(p.contaRecebimento());
              boolean eligible =
                  p.status().equals("ABERTA")
                      && p.dataDesconto().isAfter(LocalDate.now(ZoneOffset.UTC))
                      && !reserved.contains(p.parcelaId());
              return new Installment(
                  p.parcelaId(),
                  p.convenioId(),
                  p.valor(),
                  p.dataDesconto(),
                  p.status(),
                  eligible,
                  account);
            })
        .toList();
  }

  private Operation existing(String key, OperationRequest request) {
    var found = operations.findByKey(key);
    if (found.isEmpty()) return null;
    if (!request.equals(found.get().solicitacao().normalized()))
      throw new DomainException(
          DomainException.Kind.CONFLICT,
          "CHAVE_REUTILIZADA",
          "Esta chave de idempotência já foi usada com outro conteúdo.");
    return found.get();
  }

  public Operation create(String key, OperationRequest raw) {
    if (key == null || !key.matches("[a-zA-Z0-9_-]{8,100}"))
      throw new DomainException(
          DomainException.Kind.INVALID_REQUEST,
          "CHAVE_INVALIDA",
          "Envie Idempotency-Key com 8 a 100 caracteres seguros.");
    var request = raw.normalized();
    var found = existing(key, request);
    if (found != null) return found;
    var eligible =
        new HashSet<>(
            agenda(request.beneficiarioId()).stream()
                .filter(Installment::elegivel)
                .map(Installment::parcelaId)
                .toList());
    if (!eligible.containsAll(request.parcelas())) {
      found = existing(key, request);
      if (found != null) return found;
      throw new DomainException(
          DomainException.Kind.CONFLICT,
          "PARCELA_INELEGIVEL",
          "Uma ou mais parcelas não estão elegíveis. Atualize a agenda.");
    }
    String id = UUID.randomUUID().toString();
    try {
      tx.execute(
          () -> {
            operations.insertIntent(id, key, request, Instant.now());
            for (String installment : request.parcelas())
              operations.reserve(request.beneficiarioId(), installment, id);
            operations.event(
                id,
                "SOLICITADA",
                "Solicitação registrada; aguardando envio à Central. Conta ainda não alterada.");
          });
    } catch (PersistenceConflict e) {
      found = existing(key, request);
      if (found != null) return found;
      throw new DomainException(
          DomainException.Kind.CONFLICT,
          "PARCELA_RESERVADA",
          "Outra operação já reservou uma destas parcelas.");
    }
    return operations.operation(id);
  }

  public Operation resume(String id) {
    tx.execute(
        () -> {
          operations.lock(id);
          var op = operations.operation(id);
          if (!op.status().equals("REVISAO_NECESSARIA"))
            throw new DomainException(
                DomainException.Kind.CONFLICT,
                "TRANSICAO_INVALIDA",
                "Somente operações que precisam de revisão podem ser retomadas.");
          operations.resume(id, op.protocolo() == null ? "RECEBIDA" : "PENDENTE", Instant.now());
          operations.event(
              id, "RETOMADA", "Retomada manual; chave de idempotência e protocolo preservados.");
        });
    return operations.operation(id);
  }

  public Operation get(String id) {
    return operations.operation(id);
  }

  public List<Operation> list() {
    return operations.all();
  }
}
