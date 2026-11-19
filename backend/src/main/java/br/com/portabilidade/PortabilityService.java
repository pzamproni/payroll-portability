package br.com.portabilidade;

import br.com.portabilidade.Models.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "api")
public class PortabilityService {
  private final Store store;
  private final CentralClient central;
  private final TransactionTemplate tx;

  public PortabilityService(Store store, CentralClient central, TransactionTemplate tx) {
    this.store = store;
    this.central = central;
    this.tx = tx;
  }

  public Authorization authorize(AuthorizationRequest request) {
    var existing = authorization(request.beneficiarioId(), request.convenioId());
    if (existing != null) return existing;
    var auth = central.authorize(request);
    try {
      tx.executeWithoutResult(
          s ->
              store.db.update(
                  "INSERT INTO authorizations(id,beneficiary,agreement,created_at) VALUES(?,?,?,?)",
                  auth.id(),
                  auth.beneficiarioId(),
                  auth.convenioId(),
                  Store.ts(Instant.now())));
    } catch (DataIntegrityViolationException e) {
      var concurrent = authorization(request.beneficiarioId(), request.convenioId());
      if (concurrent == null) throw e;
      return concurrent;
    }
    return auth;
  }

  private Authorization authorization(String beneficiary, String agreement) {
    var auth =
        store.db.query(
            "SELECT * FROM authorizations WHERE beneficiary=? AND agreement=?",
            (rs, n) -> new Authorization(rs.getString("id"), beneficiary, agreement),
            beneficiary,
            agreement);
    return auth.isEmpty() ? null : auth.getFirst();
  }

  public List<Installment> agenda(String beneficiary) {
    if (!beneficiary.matches("[a-zA-Z0-9_-]{1,80}"))
      throw new DomainException(400, "BENEFICIARIO_INVALIDO", "Identificador inválido.");
    if (!store.authorized(beneficiary))
      throw new DomainException(
          403, "SEM_AUTORIZACAO", "Registre o opt-in antes de consultar a agenda.");
    var remote = central.agenda(beneficiary);
    var reserved =
        new HashSet<>(
            store.db.queryForList(
                "SELECT installment_id FROM reservations WHERE beneficiary=?",
                String.class,
                beneficiary));
    return remote.stream()
        .filter(p -> store.agreementAuthorized(beneficiary, p.convenioId()))
        .map(
            p -> {
              var accounts =
                  store.db.queryForList(
                      "SELECT account_json FROM receiving_accounts WHERE beneficiary=? AND"
                          + " installment_id=?",
                      String.class,
                      beneficiary,
                      p.parcelaId());
              Account account =
                  accounts.isEmpty()
                      ? p.contaRecebimento()
                      : store.json.read(accounts.getFirst(), Account.class);
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

  private Operation existing(String key, String hash) {
    var rows =
        store.db.queryForList(
            "SELECT id,payload_hash FROM operations WHERE idempotency_key=?", key);
    if (rows.isEmpty()) return null;
    if (!hash.equals(rows.getFirst().get("payload_hash")))
      throw new DomainException(
          409, "CHAVE_REUTILIZADA", "Esta chave de idempotência já foi usada com outro conteúdo.");
    return store.operation((String) rows.getFirst().get("id"));
  }

  public Operation create(String key, OperationRequest raw) {
    if (key == null || !key.matches("[a-zA-Z0-9_-]{8,100}"))
      throw new DomainException(
          400, "CHAVE_INVALIDA", "Envie Idempotency-Key com 8 a 100 caracteres seguros.");
    var request = raw.normalized();
    String payload = store.json.write(request), hash = Hmac.hash(payload);
    var found = existing(key, hash);
    if (found != null) return found;
    var agenda = agenda(request.beneficiarioId());
    var eligible =
        new HashSet<>(
            agenda.stream().filter(Installment::elegivel).map(Installment::parcelaId).toList());
    if (!eligible.containsAll(request.parcelas())) {
      found = existing(key, hash);
      if (found != null) return found;
      throw new DomainException(
          409,
          "PARCELA_INELEGIVEL",
          "Uma ou mais parcelas não estão elegíveis. Atualize a agenda.");
    }
    String id = UUID.randomUUID().toString();
    try {
      tx.executeWithoutResult(
          s -> {
            var now = Store.ts(Instant.now());
            store.db.update(
                "INSERT INTO"
                    + " operations(id,idempotency_key,payload_hash,payload,beneficiary,status,next_attempt_at,created_at,updated_at)"
                    + " VALUES(?,?,?,?,?,'RECEBIDA',?,?,?)",
                id,
                key,
                hash,
                payload,
                request.beneficiarioId(),
                now,
                now,
                now);
            for (String installment : request.parcelas())
              store.db.update(
                  "INSERT INTO reservations(beneficiary,installment_id,operation_id) VALUES(?,?,?)",
                  request.beneficiarioId(),
                  installment,
                  id);
            store.event(
                id,
                "SOLICITADA",
                "Solicitação registrada; aguardando envio à Central. Conta ainda não alterada.");
          });
    } catch (DataIntegrityViolationException e) {
      found = existing(key, hash);
      if (found != null) return found;
      throw new DomainException(
          409, "PARCELA_RESERVADA", "Outra operação já reservou uma destas parcelas.");
    }
    return store.operation(id);
  }

  public Operation resume(String id) {
    tx.executeWithoutResult(
        s -> {
          store.db.queryForList("SELECT id FROM operations WHERE id=? FOR UPDATE", id);
          var op = store.operation(id);
          if (!op.status().equals("REVISAO_NECESSARIA"))
            throw new DomainException(
                409,
                "TRANSICAO_INVALIDA",
                "Somente operações que precisam de revisão podem ser retomadas.");
          store.db.update(
              "UPDATE operations SET"
                  + " status=?,attempts=0,next_attempt_at=?,last_error=NULL,updated_at=? WHERE"
                  + " id=?",
              op.protocolo() == null ? "RECEBIDA" : "PENDENTE",
              Store.ts(Instant.now()),
              Store.ts(Instant.now()),
              id);
          store.event(
              id, "RETOMADA", "Retomada manual; chave de idempotência e protocolo preservados.");
        });
    return store.operation(id);
  }
}
