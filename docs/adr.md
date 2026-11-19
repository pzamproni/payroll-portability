# Decisões arquiteturais

## ADR-001 — Dois processos Java, mesmo artefato
Spring Boot 3.5 + Java 21, perfis por `APP_ROLE=api|central`, HTTP real entre serviços. Reutiliza infraestrutura de assinatura e build, separa responsabilidades em classes e bancos distintos. Evita duplicar dois projetos sem esconder as falhas de rede numa chamada local.

## ADR-002 — PostgreSQL e JDBC explícito
PostgreSQL garante unicidade, transações e locks; Flyway versiona o schema. JdbcTemplate deixa as invariantes e transações visíveis no code review. H2 em modo PostgreSQL serve aos testes rápidos; os cenários de Compose exercitam PostgreSQL real.

## ADR-003 — Registro local antes de HTTP
A transação inicial persiste intenção e reservas; um worker consulta a fila no próprio banco. A operação é o outbox durável, com chave original enviada à Central. Locks por operação impedem dois workers de processarem o mesmo registro; as transações incluem chamadas HTTP curtas com timeout para simplificar exclusão mútua. Trade-off: ocupam conexão durante o HTTP; evolução: leases e outbox separado. Nunca criar uma chave nova num retry.

## ADR-004 — Auditoria append-only
Eventos e estado/conta são gravados na mesma transação. Trigger PostgreSQL bloqueia UPDATE/DELETE na tabela de eventos. Não é ledger criptográfico nem protege contra administrador do banco.

## ADR-005 — Falhas determinísticas e UI operacional
Cenários explícitos tornam demonstração e testes reproduzíveis. Painel apresenta erros e retomada, sem perder status remoto incerto. Frontend usa polling com cancelamento na desmontagem, pausa em aba oculta e atualização da agenda após estados terminais.

## ADR-006 — Docker multistage
Maven compila JAR, JRE executa com usuário sem privilégios; Node compila React, Nginx sem root serve estáticos e faz proxy para API. Postgres e Central não são publicados no host por padrão. Segredo HMAC vem do ambiente, não do bundle frontend.
