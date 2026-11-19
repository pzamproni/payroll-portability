# Especificação funcional — v1

Fonte: desafio técnico Fullstack React + Java fornecido pelo candidato. SDD significa Specification-Driven Development: requisitos → contratos → decisões → implementação → evidências. Estes arquivos foram escritos antes da implementação.

## Escopo e critérios de aceite

| ID | Requisito | Critério verificável |
|---|---|---|
| RF01 | Opt-in | Identificador único, consentimento explícito, beneficiário e convênio; sem autorização a agenda retorna 403. |
| RF02 | Agenda | Valor decimal, data, convênio e status; somente parcelas ABERTA, futuras e não reservadas são elegíveis. |
| RF03 | Portabilidade | Uma ou mais parcelas do mesmo beneficiário, autorizadas, conta bancária válida; reserva atômica impede operações concorrentes sobre a mesma parcela. |
| RF04 | Assíncrono | API persiste e responde 202; worker recebe protocolo e consulta até confirmação/rejeição; conta permanece inalterada até CONFIRMADA. |
| RF05 | Auditoria | Eventos em ordem crescente, timestamp UTC, tipo e detalhe; sem endpoint de edição/exclusão e bloqueio de UPDATE/DELETE em PostgreSQL. |
| RF06 | Falhas | Exibir indisponibilidade, timeout, resposta inválida e HMAC inválido, tentativas e próxima tentativa; sem transformar falha técnica em rejeição de negócio. |
| RNF01 | Integridade | HMAC-SHA256 em todas as requisições e respostas da Central; assinatura vinculada ao método, caminho, timestamp, nonce, chave idempotente e bytes do corpo. Janela de 5 minutos. |
| RNF02 | Idempotência | Mesma chave + mesmo conteúdo retorna mesma operação; mesma chave + conteúdo diferente retorna 409. Restrição UNIQUE e transação nos dois serviços. Timeout após aceite remoto não duplica. |
| RNF03 | Retry | Backoff exponencial com jitter limitado, respeito a Retry-After, timeout HTTP e limite de tentativas. Após limite, revisão manual com retomada segura, sem perder protocolo. |
| RNF04 | Persistência | Operações, reservas, autorizações, auditoria e agendamento sobrevivem ao reinício de cada serviço. |
| RNF05 | Entrega | React/TypeScript, Java 21, testes críticos, README, especificações, decisões técnicas, evidências e Dockerfiles multistage. |

## Cenários de demonstração determinísticos

NORMAL confirma depois de alguns segundos; REJECT rejeita; UNAVAILABLE retorna 503 duas vezes; TIMEOUT excede timeout duas vezes; TIMEOUT_AFTER_ACCEPTING persiste e atrasa a primeira resposta; INVALID_RESPONSE retorna corpo inválido duas vezes; TAMPERED_RESPONSE viola assinatura duas vezes. A falha ocasional é reproduzível, não aleatória.

## Limites explícitos

Dados exclusivamente fictícios. Sem autenticação de usuário por estar fora do enunciado; publicar o código não significa expor o serviço financeiro à internet. Não há transferência de dinheiro. Central é um simulador isolado e persistente. Um worker por instância, múltiplas instâncias protegidas por lock transacional da operação.

## Critérios de aceite executáveis

Todos os critérios detalhados estão em [Gherkin em português](acceptance.feature). Cada cenário AC possui um teste unitário, de componente ou de integração específico em [acceptance-map.json](acceptance-map.json). O verificador `python3 scripts/check_acceptance.py` falha se um cenário não estiver mapeado ou se o teste correspondente não existir. Os testes são executados pelo Maven/Vitest; não há dependência de Cucumber ou interpretação automática dos passos. Testes de Compose e Playwright complementam essa cobertura.
