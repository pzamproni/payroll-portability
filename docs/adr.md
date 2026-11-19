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

## ADR-007 — Núcleo hexagonal com portas reais

A implementação inicial concentrava regras, JDBC e transações em serviços Spring. A refatoração introduz portas de entrada (`PortabilityUseCases`, `OperationProcessing`), portas de saída (`AuthorizationRepository`, `AgendaStateRepository`, `OperationRepository`, `CentralGateway`, `UnitOfWork`) e adapters concretos. Domínio e aplicação dependem exclusivamente de Java e dessas abstrações. O bootstrap Spring cria os casos de uso; controllers não conhecem repositórios nem implementações de serviços.

O simulador é um sistema externo iniciado em outro processo. Seu código está em `simulator/`, sem dependência a partir do núcleo da plataforma. Seus próprios detalhes SQL não são regras de negócio do produto. O contrato HTTP mantém nomes de campos e estados em português por compatibilidade; a identidade, os pacotes e os artefatos do produto são em inglês.

As fronteiras são protegidas por quatro testes ArchUnit. Monorepo e arquitetura interna são decisões independentes: não foi necessário separar repositórios nem criar microserviços adicionais para obter inversão de dependências.

## ADR-008 — Identidade inglesa e preservação dos dados locais

Nome do produto: Payroll Portability. Repositório e pasta: `payroll-portability`; pacote Java: `com.payrollportability`; artefato Maven: `payroll-portability`; imagem: `payroll-portability-java`.

Clones novos usam banco/usuário/volume com nomes em inglês. A instalação já demonstrada reutiliza seu volume por `.env` com `POSTGRES_VOLUME_NAME`, `DATABASE_USER` e `DATABASE_NAME` explícitos. Nomes legados de armazenamento ficam apenas nessa configuração local, não na identidade do projeto. As migrações Flyway existentes e os payloads persistidos não foram reescritos. A igualdade da intenção normalizada continua decidindo conflitos idempotentes, sem depender da serialização HTTP no núcleo.

Referência da ferramenta de verificação: https://www.archunit.org/userguide/html/000_Index.html
