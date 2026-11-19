# Plano de verificação

1. Testes Java: validação e opt-in; idempotência sequencial e concorrente; conflito de conteúdo; reserva concorrente; HMAC/timestamp/corpo adulterado; retry/backoff; limite e retomada; protocolo preservado; confirmação altera conta atomicamente; rejeição preserva conta/libera parcela; auditoria e estado após reinício lógico.
2. Testes de contrato HTTP real da Central: assinatura de ida e volta, 202, 204 e Retry-After, rejeição, indisponibilidade, resposta inválida, timeout depois do aceite.
3. Frontend: typecheck, build, testes de formulário, tratamento de erro e preservação da chave idempotente em reenvio.
4. Docker Compose: build multistage, healthchecks, fluxo completo sobre PostgreSQL, cenários determinísticos, repetição e concorrência; reinício dos serviços e recuperação persistente.
5. Browser: opt-in, seleção, conta, envio, atualização automática, eventos e rejeição; layout desktop e móvel.

Registrar resultados reais em `docs/verification.md`. Não declarar execução que não ocorreu.
