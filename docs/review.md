# Revisão contra o PDF

Revisão de 19/11/2026. O PDF fornecido é a fonte dos requisitos do desafio; as instruções posteriores do usuário acrescentam monorepo, arquitetura hexagonal, Docker multistage, Gherkin com teste por cenário e remoção do arquivo separado de harness. O processo de IA foi consolidado no README para atender à documentação exigida sem recriar esse arquivo.

## Matriz de conformidade

| Requisito do PDF | Implementação e critérios | Evidência executável |
|---|---|---|
| Opt-in vinculado a identificador único | Autorização por beneficiário/convênio, consentimento explícito e resposta remota validada | PortabilityTest, ApiValidationTest, CentralHttpTest, CentralClientContractTest, App.test.tsx |
| Agenda futura com valor/data/convênio/status | Consulta assinada, filtro por data UTC e convênio consentido | AC-062/063; agenda e consentimento nos critérios anteriores |
| Seleção de uma ou mais parcelas e conta destino | Elegibilidade, reservas exclusivas, conta validada; seleção mista falha integralmente | AC-057/058 e validação HTTP |
| Confirmação assíncrona | 202 + protocolo, polling 204/200, backoff/jitter/Retry-After, retomada segura | PortabilityTest, CentralHttpTest, smoke.py, Playwright |
| Atualização da conta após confirmação | Conta, estado e eventos na mesma transação | AC-057/059; sucesso/rejeição no PostgreSQL e navegador |
| Trilha por operação e mudanças não sobrescritas | Eventos persistentes append-only, projeção imutável, trigger PostgreSQL | AC-047/064; persistence.py testa UPDATE e DELETE |
| Rejeição e erros visíveis | Motivo de negócio, 503, timeout, timeout após aceite, JSON/HMAC inválidos | Contratos Java, sete cenários HTTP, rejeição no navegador; AC-065/067 |
| Integridade das mensagens | HMAC de requisição e resposta, corpo/status/caminho/chave/nonce/timestamp | HmacTest e CentralHttpTest; adulteração no smoke |
| Idempotência nos dois sistemas | Chaves/fingerprints únicos, transações, reservas e rechecagem após locks | Concorrência nos testes Java/PostgreSQL; AC-060/061 |
| Manutenção e testes críticos | Domínio e aplicação Java puro, portas e adaptadores, regras ArchUnit | AC-048/049/050/051/056; 67 critérios com resultado individual |
| Java, React e mock da Central | Java 21/Spring, React/TypeScript, processo Central com banco separado | Build multistage e quatro healthchecks |
| README, libs, decisões, trade-offs e IA | Execução conjunta/separada, tabela de libs, ADRs, prompts/restrições/resultados | README, specs, docs/adr.md e evidências versionadas |
| Repositório público | Monorepo frontend/backend em pzamproni/payroll-portability | GitHub e workflow Verify |

O contrato da Central é sugerido no PDF. A assinatura é enviada em headers, cobrindo também o corpo, em vez de um campo JSON; o polling usa a alternativa 204 com Retry-After explicitamente permitida. O estado local RECEBIDA representa a intenção ainda não enviada; PENDENTE contém o protocolo remoto.

## Defeitos corrigidos

- Agenda remota com item nulo gerava erro não controlado. IDs repetidos, valor não positivo e status desconhecido eram aceitos; o adaptador agora recusa esses contratos explicitamente.
- Motivo remoto maior que a coluna de erro podia impedir progresso do worker. A fronteira HTTP valida o limite antes de persistir.
- Uma solicitação concorrente podia confirmar antes de um replay obter o lock, fazendo o replay falhar por inelegibilidade. A Central agora reconsulta a chave após os locks e antes da elegibilidade. O teste usa outra transação real para reproduzir a ordem.
- Agenda podia mostrar parcelas passadas. API e simulador filtram datas futuras; o teste também verifica isolamento do convênio autorizado.
- A projeção de eventos guardava lista mutável. Agora possui cópia defensiva imutável.
- Corpo de erro JSON nulo no navegador podia causar TypeError. A UI normaliza erros inesperados.
- Uma falha de agenda após confirmação impedia tentativas posteriores de atualização automática. O marcador de atualização só avança após sucesso, com regressão no frontend.
- A regra arquitetural conjunta permitia domínio depender de aplicação. Uma quarta regra ArchUnit impede essa direção.

## Lacunas de verificação fechadas

Além das regressões, foram acrescentadas provas diretas de confirmação de múltiplas parcelas, seleção mista sem efeitos parciais, rollback de conta/estado/eventos quando uma gravação de auditoria falha e timeout antes do aceite. A retomada agora é testada contra todos os estados que não permitem retomada.

O teste de persistência tenta UPDATE e DELETE somente sobre sua própria operação, dentro de transações revertidas mesmo se o trigger estiver ausente. O verificador Gherkin exige classe/método corretos, teste distinto por critério e resultado aprovado sem skip. O relatório Python identifica explicitamente a função executada.

## Dependências

Nenhuma dependência de produção foi acrescentada. O agregado de testes já havia sido substituído pelas bibliotecas efetivamente usadas. O JAR contém 47 bibliotecas de runtime e nenhuma biblioteca de testes; essa separação agora tem verificação no CI. Imports explícitos continuam obrigatórios. A auditoria npm desta revisão retornou zero vulnerabilidades; isso registra o estado consultado, sem prometer ausência permanente de vulnerabilidades.

As ações do GitHub foram atualizadas a partir das releases oficiais para evitar runtimes obsoletos. O frontend final contém somente estáticos servidos por Nginx; Maven, Node, ferramentas e bibliotecas de teste permanecem nos estágios de build.

## Resultados e limites

Os resultados atuais estão em [verification.md](verification.md), com resumos gerados dos relatórios reais em evidence/. Os testes Java/H2 não substituem PostgreSQL: concorrência, recuperação e proteção append-only são verificados também no Compose. As duas jornadas Playwright complementam os testes unitários e de integração.

Permanecem os trade-offs documentados no README: sem autenticação de usuários, banco com fila de trabalho e lock durante HTTP curto, segredo HMAC compartilhado, sem reconciliação bancária real, paginação ou prova de carga distribuída. Esses itens não são requisitos adicionais do PDF e não são apresentados como capacidades implementadas.
