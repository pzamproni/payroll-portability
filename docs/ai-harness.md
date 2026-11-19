# Harness e uso de IA

## Ambiente e processo
Agente Codex com ferramentas de shell, leitura de PDF, pesquisa nas documentações oficiais e integração GitHub. Desenvolvimento SDD: ler enunciado inteiro, escrever especificação versionada, contratos, decisões e plano de verificação antes do código. Sem subagentes nesta entrega. O agente implementa e verifica; resultados de execução são evidência, não a afirmação do modelo.

## Prompts e restrições recebidos
- Pedido: analisar PDF, criar projeto, publicar no GitHub pessoal, precisão, SDD, documento externo explicando como e por quê e Docker sempre multistage.
- Prazo operacional: poucas horas. O usuário menciona três dias no briefing; o PDF anexado diz apenas “prazo combinado”, sem prazo numérico. A implementação prioriza invariantes e caminho completo verificável.
- Conta de publicação escolhida explicitamente pelo usuário: `pzamproni`.
- Restrições de engenharia: Java + React; mensagens assinadas; idempotência local e remota; polling assíncrono com backoff; auditoria imutável; testes críticos; não publicar credenciais/dados reais.

## Ciclo de trabalho
1. Extração integral e inspeção visual do PDF.
2. Conversão dos requisitos em RF/RNF e cenários verificáveis.
3. Código guiado pelos contratos; alterações de implementação devem reconciliar os documentos.
4. Testes automatizados e execução real; corrigir divergências encontradas.
5. Registro dos resultados, limitações e trade-offs, revisão dos arquivos publicados.

## O que a IA produziu
Especificações, arquitetura, implementação Java/React, simulador HTTP, testes, Docker, CI, README e relatório externo. As evidências e correções são registradas em `verification.md`; não há alegação de revisão humana independente. Não foram utilizadas APIs pagas de IA dentro do produto.

## Referências consultadas
- https://docs.spring.io/spring-boot/3.5/system-requirements.html
- https://react.dev/versions
- https://vite.dev/guide/

Versões efetivamente resolvidas estão em `pom.xml` e `package-lock.json`; documentação ao vivo pode mencionar releases ainda não disponíveis no registry do ambiente.

## Orientações adicionais incorporadas

- Java 21 LTS confirmado ao usuário.
- Frontend e backend no mesmo monorepo.
- Critérios Gherkin em português com teste individual por cenário; 47 cenários mapeados 1:1 a testes distintos. O verificador também cruza os resultados executados.
- Publicação na conta pzamproni; navegador interno para criação do repositório e SSH para envio do Git.
