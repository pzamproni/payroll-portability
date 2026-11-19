# Verificação da entrega

Execução local em 19/11/2026, macOS/ARM64, Java 21 e Docker/Colima. Resultado: os quatro serviços ficaram healthy e o fluxo completo foi executado sobre PostgreSQL real. O repositório contém somente dados fictícios.

| Verificação | Resultado | Evidência |
|---|---|---|
| Java / Maven verify | 60 testes, zero falhas, zero erros e zero ignorados | [Resumo por suíte](evidence/java-tests.json) |
| Vitest | 6 testes passaram | [Casos executados](evidence/frontend-tests.json) |
| Gherkin | 67 cenários → 67 testes distintos, todos com resultado aprovado | [Verificador](evidence/acceptance.txt) |
| TypeScript / Vite | typecheck e build de produção passaram | `npm run build` |
| Dependências npm | zero vulnerabilidades no momento da checagem | `npm audit --audit-level=moderate` |
| Docker | build multistage Java/React com testes; database/central/api/web healthy | `docker compose ps` |
| Integração PostgreSQL | 7 cenários passaram; 6 envios simultâneos por cenário | [Resultados reais](evidence/smoke.json) |
| Persistência / auditoria | reinício dos quatro serviços, protocolo preservado, uma operação em cada banco, UPDATE e DELETE de auditoria bloqueados | [Resultado](evidence/persistence.json) |
| Navegador | 2 jornadas passaram: confirmação e rejeição; layout 390 px sem overflow | [Playwright](evidence/playwright.txt) |
| Cobertura Java local | 86,3% das linhas; 77,5% das branches | [JaCoCo resumido](evidence/coverage.json) |

As métricas de cobertura se referem aos testes Java locais; os cenários PostgreSQL/Playwright são executados fora da JVM instrumentada. Não representam uma afirmação de ausência de defeitos.

## Jornadas verificadas

- Agenda sem opt-in negada; autorização repetida mantém o identificador.
- Valores, datas futuras, convênio e status; somente quatro das seis parcelas fictícias elegíveis.
- Reenvios concorrentes com mesma chave retornam o mesmo ID; outra chave não captura parcela reservada; alteração do conteúdo com a mesma chave retorna 409.
- Conta permanece inalterada antes da confirmação; confirmação atualiza conta e eventos; rejeição preserva conta e libera a parcela.
- 503, timeout, timeout após aceite, JSON inválido e assinatura adulterada são auditados e recuperados sem duplicidade.
- Limite de tentativas e retomada com protocolo preservado testados no backend.
- Reinício do banco e dos serviços preserva dados e permite finalizar a operação; trigger PostgreSQL bloqueia alteração do histórico.
- UI exige consentimento, mostra motivo da rejeição e acompanha confirmação, conta e trilha automaticamente.

## Correções orientadas pelas verificações

1. A primeira compilação identificou escaping inválido numa resposta de erro do filtro; corrigido antes dos testes.
2. Um teste Mockito estava reconfigurando um método que já lançava exceção; corrigido com `doReturn`, mantendo o teste de retry.
3. O build tentou baixar dependências opcionais de plugins em excesso; removido `dependency:go-offline`, mantendo build/testes no estágio Maven.
4. Vitest inicialmente descobria testes Playwright; configuração limitada a `src/**/*.test.{ts,tsx}`.
5. Auditoria npm encontrou vulnerabilidades em Vitest/tinypool; atualização para Vitest 4.1.12 eliminou os avisos disponíveis no registry consultado.
6. Cliente HTTP endurecido para JSON nulo e status ausente, com testes específicos; polling React cancelado por AbortController.
7. Critérios Gherkin receberam mapeamento 1:1 e checagem dos resultados reais, conforme orientação adicional do usuário.

## Limites da evidência

A validação de browser usou Chromium. Não foram executados testes de carga prolongados, múltiplas instâncias distribuídas, pentest ou uma integração bancária real. A regra de imutabilidade foi exercitada via UPDATE e DELETE sobre a operação criada pelo próprio teste, em transações revertidas. O sistema não tem autenticação/RBAC ou consentimento revogável, recursos fora do recorte da demonstração. O build usa tags de imagens oficiais; para reprodução binária de longo prazo, fixe também seus digests.

O workflow de CI está versionado; seu resultado remoto deve ser consultado na aba Actions. Este documento descreve os resultados locais e não antecipa resultado remoto.

## Capturas da aplicação

[Desktop](screenshots/desktop.png) · [Celular](screenshots/mobile.png)

## Demonstração manual no navegador interno

Em 19/11/2026, os sete cenários foram executados pela interface com beneficiários fictícios novos. A confirmação normal portou duas parcelas (R$ 715,00). A rejeição mostrou o motivo, preservou a conta e liberou a parcela; as cinco falhas de integração foram auditadas e recuperaram até CONFIRMADA. Uma consulta SQL somente leitura confirmou exatamente uma operação na Central para cada beneficiário, inclusive nos timeouts.

[Identificadores, protocolos e trilhas da demonstração](evidence/demonstracao-manual.json).

## Refatoração e identidade Payroll Portability

- Pasta e repositório renomeados para `payroll-portability`; coordenadas Maven, imagens, pacote Java e nome da interface em inglês.
- Núcleo Java independente de Spring/JDBC/HTTP/JSON, com portas reais e quatro regras ArchUnit.
- Imports explícitos em todos os fontes Java e regra de build contra curingas; TypeScript rejeita imports/locais/parâmetros não usados.
- Test starter agregado substituído pelas bibliotecas usadas: classpath de compile/runtime/test caiu de 85 para 75 JARs. Nenhuma biblioteca de testes foi incluída no artefato de produção; [medição](evidence/dependencies.json).
- Volume de dados existente preservado como externo. Configuração com nomes ingleses também verificada em um Postgres temporário isolado, removido após o teste.
- A falta de espaço do host interrompeu um build Docker. Cache de downloads regenerável foi limpo e a VM reiniciada; nenhum volume persistente foi apagado. A reconstrução e os healthchecks passaram após a recuperação.

## Revisão completa contra o PDF

A [matriz de revisão](review.md) relaciona os requisitos à implementação e aos testes. Foram acrescentados 16 cenários, elevando a cobertura de aceite de 51 para 67 critérios distintos: 60 testes Java, 6 testes frontend e 1 teste de persistência PostgreSQL. Dois fluxos Playwright e os sete cenários HTTP complementam esse conjunto.

A revisão corrigiu contratos remotos inválidos, corrida idempotente após confirmação, agenda com datas passadas, mutabilidade da projeção de eventos, erros JSON inesperados no navegador e recuperação da atualização da agenda. Também acrescentou provas de múltiplas parcelas, seleção mista atômica e rollback de conta/estado/auditoria. O verificador de aceite agora confere classe e nome do teste, além do resultado sem falha ou skip.

O JAR de produção foi inspecionado: 47 bibliotecas de runtime, nenhuma biblioteca de testes. [Resultado](evidence/runtime-artifact.json). Nenhuma dependência de produção foi adicionada. A [auditoria npm](evidence/npm-audit.json) retornou zero vulnerabilidades nesta execução. As ações do CI foram atualizadas para releases oficiais com runtime atual.

## Revisão completa contra o PDF

A [matriz de revisão](review.md) relaciona os requisitos à implementação e aos testes. Foram acrescentados 16 cenários, elevando a cobertura de aceite de 51 para 67 critérios distintos: 60 testes Java, 6 testes frontend e 1 teste de persistência PostgreSQL. Dois fluxos Playwright e os sete cenários HTTP complementam esse conjunto.

A revisão corrigiu contratos remotos inválidos, corrida idempotente após confirmação, agenda com datas passadas, mutabilidade da projeção de eventos, erros JSON inesperados no navegador e recuperação da atualização da agenda. Também acrescentou provas de múltiplas parcelas, seleção mista atômica e rollback de conta/estado/auditoria. O verificador de aceite agora confere classe e nome do teste, além do resultado sem falha ou skip.

O JAR de produção foi inspecionado: 47 bibliotecas de runtime, nenhuma biblioteca de testes. [Resultado](evidence/runtime-artifact.json). Nenhuma dependência de produção foi adicionada. A [auditoria npm](evidence/npm-audit.json) retornou zero vulnerabilidades nesta execução. As ações do CI foram atualizadas para releases oficiais com runtime atual.
