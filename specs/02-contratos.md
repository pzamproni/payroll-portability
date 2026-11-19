# Contratos HTTP — v1

API pública de demonstração em `/api`. Erros: `{code,message}`. Campos e mensagens em português; horários ISO-8601 UTC; valores JSON decimais.

- `POST /api/autorizacoes`: `{beneficiarioId,convenioId,consentimento:true}` → 201 `{id,beneficiarioId,convenioId}`; repetição devolve autorização existente.
- `GET /api/beneficiarios/{id}/agenda` → 200 parcelas `{parcelaId,convenioId,valor,dataDesconto,status,elegivel,contaRecebimento}`; 403 sem opt-in.
- `POST /api/operacoes`, header obrigatório `Idempotency-Key` (UUID recomendado): `{beneficiarioId,parcelas:[id],credorCessionario,contaRecebimento:{banco,agencia,conta},cenario}` → 202 operação persistida, `Location`; 409 chave reutilizada com outro payload ou parcela reservada.
- `GET /api/operacoes` e `GET /api/operacoes/{id}` → operação, protocolo, conta destino, tentativas, erro, próxima tentativa e trilha de eventos.
- `POST /api/operacoes/{id}/retomar` → 202; somente REVISAO_NECESSARIA; preserva chave, parcelas e protocolo.
- `GET /actuator/health` → liveness/readiness de cada serviço.

## Central (rede interna)

`POST /autorizacoes`, `GET /beneficiarios/{id}/agenda`, `POST /operacoes-portabilidade` → 202 `{protocolo}`, `GET /operacoes-portabilidade/{protocolo}` → 204 + Retry-After ou 200 `{status,motivoRejeicao}`. Contrato do enunciado preservado; HMAC em headers substitui assinatura dentro do JSON para evitar ambiguidade na serialização.

Headers: `X-Timestamp`, `X-Nonce`, `X-Signature`, `Idempotency-Key`. Request canônica: `METHOD\nPATH\nTIMESTAMP\nNONCE\nIDEMPOTENCY_KEY\nSHA256(body)`. Resposta: `STATUS\nTIMESTAMP\nNONCE_DA_REQUISICAO\nSHA256(body)`. Hex HMAC comparado em tempo constante. O nonce vincula resposta à requisição; efeito de replay mutante é impedido pelas chaves idempotentes e unicidade do opt-in.

## Máquina de estados

RECEBIDA → PENDENTE → CONFIRMADA | REJEITADA.
RECEBIDA ou PENDENTE → REVISAO_NECESSARIA em falha técnica não recuperável/limite. Retomada retorna a PENDENTE quando existe protocolo; caso contrário RECEBIDA. Nenhuma falha técnica implica rejeição do negócio. Apenas confirmação grava conta e libera processamento terminal; rejeição libera reservas. CONFIRMADA e REJEITADA são finais.
