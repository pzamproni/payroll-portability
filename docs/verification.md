# Verificação da entrega

Execução local em 19/11/2026, macOS/ARM64, Java 21 e Docker/Colima. Resultado: os quatro serviços ficaram healthy e o fluxo completo foi executado sobre PostgreSQL real. O repositório contém somente dados fictícios.

| Verificação | Resultado | Evidência |
|---|---|---|
| Java / Maven verify | 42 testes, zero falhas, zero erros e zero ignorados | [Resumo por suíte](evidence/java-tests.json) |
| Vitest | 4 testes passaram | [Casos executados](evidence/frontend-tests.json) |
| Gherkin | 47 cenários → 47 testes distintos, todos com resultado aprovado | [Verificador](evidence/acceptance.txt) |
| TypeScript / Vite | typecheck e build de produção passaram | `npm run build` |
| Dependências npm | zero vulnerabilidades no momento da checagem | `npm audit --audit-level=moderate` |
| Docker | build multistage Java/React com testes; database/central/api/web healthy | `docker compose ps` |
| Integração PostgreSQL | 7 cenários passaram; 6 envios simultâneos por cenário | [Resultados reais](evidence/smoke.json) |
| Persistência / auditoria | reinício dos quatro serviços, protocolo preservado, uma operação em cada banco, UPDATE de auditoria bloqueado | [Resultado](evidence/persistence.json) |
| Navegador | 2 jornadas passaram: confirmação e rejeição; layout 390 px sem overflow | [Playwright](evidence/playwright.txt) |
| Cobertura Java local | 84,6% das linhas; 73,8% das branches | [JaCoCo resumido](evidence/coverage.json) |

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

A validação de browser usou Chromium. Não foram executados testes de carga prolongados, múltiplas instâncias distribuídas, pentest ou uma integração bancária real. A regra de imutabilidade foi exercitada via UPDATE; o mesmo trigger também cobre DELETE por definição da migração. O sistema não tem autenticação/RBAC ou consentimento revogável, recursos fora do recorte da demonstração. O build usa tags de imagens oficiais; para reprodução binária de longo prazo, fixe também seus digests.

O workflow de CI está versionado; seu resultado remoto deve ser consultado na aba Actions. Este documento descreve os resultados locais e não antecipa resultado remoto.

## Capturas da aplicação

[Desktop](screenshots/desktop.png) · [Celular](screenshots/mobile.png)
