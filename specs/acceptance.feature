# language: pt
Funcionalidade: Portabilidade de convênios com integridade e recuperação
  A plataforma deve proteger consentimento, unicidade, confirmação e histórico.
  Cada cenário possui um teste executável específico em acceptance-map.json.

  @AC-001
  Cenário: Validar mensagem íntegra
    Dado uma mensagem assinada com segredo compartilhado
    Quando a assinatura dos mesmos bytes é verificada
    Então a verificação deve ser válida

  @AC-002
  Cenário: Recusar corpo adulterado
    Dado uma mensagem com assinatura válida
    Quando os bytes do corpo são alterados
    Então a verificação deve falhar

  @AC-003
  Cenário: Recusar mensagem expirada
    Dado uma mensagem assinada há mais de cinco minutos
    Quando sua integridade é verificada
    Então a mensagem deve ser recusada

  @AC-004
  Cenário: Recusar timestamp futuro
    Dado uma mensagem assinada com horário mais de cinco minutos no futuro
    Quando sua integridade é verificada
    Então a mensagem deve ser recusada

  @AC-005
  Cenário: Recusar assinatura malformada
    Dado uma mensagem com assinatura ausente ou não hexadecimal
    Quando sua integridade é verificada
    Então a verificação deve retornar falso sem erro não tratado

  @AC-006
  Cenário: Vincular resposta ao nonce e status HTTP
    Dado uma resposta assinada para um nonce e status específicos
    Quando o nonce ou o status é trocado
    Então a assinatura deve ser recusada

  @AC-007
  Cenário: Vincular chave idempotente à assinatura
    Dado uma requisição assinada para uma chave idempotente
    Quando a chave idempotente é alterada
    Então a assinatura deve ser recusada

  @AC-008
  Cenário: Exigir opt-in antes da consulta
    Dado um beneficiário sem autorização
    Quando sua agenda é solicitada
    Então a consulta deve ser negada sem chamada à Central

  @AC-009
  Cenário: Exigir chave de idempotência
    Dado uma solicitação de portabilidade válida
    Quando o registro é solicitado sem chave idempotente
    Então a solicitação deve ser recusada

  @AC-010
  Cenário: Persistir intenção antes do envio remoto
    Dado um beneficiário autorizado com parcela elegível
    Quando uma operação é registrada
    Então o estado deve ser RECEBIDA e o evento SOLICITADA deve existir antes de qualquer envio remoto

  @AC-011
  Cenário: Reenviar solicitação idêntica
    Dado uma operação já registrada
    Quando a mesma solicitação é reenviada com a mesma chave
    Então o mesmo identificador deve ser retornado e deve existir apenas uma operação

  @AC-012
  Cenário: Recusar conteúdo diferente com a mesma chave
    Dado uma chave já vinculada a uma operação
    Quando a chave é reutilizada com outro credor
    Então o registro deve ser recusado por conflito de conteúdo

  @AC-013
  Cenário: Reservar parcelas em operações distintas
    Dado uma parcela reservada por uma operação
    Quando outra chave tenta registrar a mesma parcela
    Então a segunda solicitação deve ser recusada e a agenda deve indicar inelegibilidade

  @AC-014
  Cenário: Recusar parcela paga ou seleção repetida
    Dado uma parcela paga e uma parcela aberta
    Quando uma solicitação seleciona a parcela paga ou repete a mesma parcela
    Então a solicitação deve ser recusada

  @AC-015
  Cenário: Deduplicar envios simultâneos
    Dado seis requisições idênticas com a mesma chave
    Quando o registro é executado simultaneamente
    Então todas devem retornar o mesmo identificador e apenas uma operação deve existir

  @AC-016
  Cenário: Atualizar conta somente após confirmação
    Dado uma operação aceita pela Central e uma conta ainda inalterada
    Quando a Central confirma a portabilidade
    Então a conta e os eventos CONFIRMADA e CONTA_ATUALIZADA devem ser persistidos juntos sem reprocessamento terminal

  @AC-017
  Cenário: Preservar conta na rejeição
    Dado uma operação pendente
    Quando a Central rejeita a portabilidade com motivo
    Então o estado deve ser REJEITADA e a conta deve permanecer inalterada e a reserva deve ser liberada

  @AC-018
  Cenário: Recuperar falha transitória sem nova chave
    Dado uma operação recebida e uma Central temporariamente indisponível
    Quando uma tentativa falha e a Central volta a responder
    Então a falha deve ser auditada e o retry deve respeitar Retry-After e reutilizar a chave original

  @AC-019
  Cenário: Retomar após esgotamento das tentativas
    Dado uma operação com protocolo e falhas sucessivas de consulta
    Quando o limite de tentativas é atingido e a operação é retomada
    Então o estado deve passar por REVISAO_NECESSARIA e voltar a PENDENTE preservando protocolo e eventos

  @AC-020
  Cenário: Separar falha técnica de rejeição de negócio
    Dado uma operação recebida
    Quando a Central retorna falha técnica não recuperável automaticamente
    Então o estado deve ser REVISAO_NECESSARIA e a reserva deve permanecer para reconciliação

  @AC-021
  Cenário: Impedir retomada fora do estado de revisão
    Dado uma operação em estado diferente de REVISAO_NECESSARIA
    Quando uma retomada é solicitada
    Então a transição deve ser recusada

  @AC-022
  Cenário: Limitar backoff e respeitar Retry-After
    Dado uma política de retry configurada
    Quando os atrasos são calculados para tentativas crescentes
    Então o atraso deve respeitar o teto configurado e um Retry-After superior deve prevalecer

  @AC-023
  Cenário: Recuperar intenção com novo worker
    Dado uma intenção de portabilidade persistida
    Quando uma nova instância do worker processa a fila
    Então o protocolo deve ser recuperado sem recriar a operação

  @AC-024
  Cenário: Resolver concorrência entre chaves distintas
    Dado quatro requisições com chaves distintas para a mesma parcela
    Quando o registro é executado simultaneamente
    Então exatamente uma deve ser aceita e as demais devem retornar conflito

  @AC-025
  Cenário: Executar contrato HTTP assinado completo
    Dado uma autorização e agenda obtidas por HTTP assinado
    Quando a mesma operação é enviada duas vezes e depois consultada
    Então o protocolo deve ser único e o estado deve ser CONFIRMADA e a agenda deve conter a conta de destino

  @AC-026
  Cenário: Receber rejeição assinada
    Dado uma operação de cenário REJECT enviada por HTTP
    Quando seu status é consultado
    Então a resposta deve ser REJEITADA e a conta da agenda deve permanecer inalterada

  @AC-027
  Cenário: Tratar resposta de integração inválida
    Dado uma operação de cenário INVALID_RESPONSE
    Quando a Central responde com corpo inválido
    Então o cliente deve expor uma falha explícita de JSON inválido

  @AC-028
  Cenário: Recuperar resposta adulterada sem duplicar na Central
    Dado uma operação de cenário TAMPERED_RESPONSE
    Quando duas respostas adulteradas são recusadas e a solicitação é reenviada
    Então deve haver apenas uma operação remota e um protocolo recuperável

  @AC-029
  Cenário: Interpretar indisponibilidade e Retry-After
    Dado uma operação de cenário UNAVAILABLE
    Quando a Central responde HTTP 503 com Retry-After
    Então o cliente deve identificar falha recuperável e preservar o atraso solicitado

  @AC-030
  Cenário: Proteger endpoints da Central
    Dado uma requisição sem assinatura
    Quando um endpoint de agenda da Central é acessado por HTTP
    Então a Central deve responder HTTP 401

  @AC-031
  Cenário: Impedir reutilização remota da chave
    Dado uma operação já aceita pela Central
    Quando a mesma chave é enviada com outro conteúdo
    Então a Central deve responder HTTP 409

  @AC-032
  Cenário: Identificar autorização de maneira única
    Dado um beneficiário e um convênio
    Quando o opt-in é enviado repetidamente à Central
    Então o mesmo identificador de autorização deve ser retornado

  @AC-033
  Cenário: Sinalizar processamento assíncrono
    Dado uma operação aceita com confirmação futura
    Quando o cliente consulta o protocolo antes da confirmação
    Então a Central deve responder 204 e o cliente deve reconhecer PENDENTE com Retry-After de dois segundos

  @AC-034
  Cenário: Resolver timeout após aceite remoto
    Dado uma operação cujo aceite remoto antecede uma resposta lenta
    Quando o cliente sofre timeout e reenvia com a mesma chave
    Então o mesmo protocolo deve ser recuperado e deve existir apenas uma operação na Central

  @AC-035
  Cenário: Exibir agenda completa do convênio autorizado
    Dado um beneficiário autorizado para um convênio
    Quando sua agenda é consultada por HTTP
    Então seis parcelas devem conter valor positivo e data futura e convênio de origem com quatro abertas uma paga e uma bloqueada

  @AC-036
  Cenário: Tratar corpo JSON nulo
    Dado uma resposta assinada contendo JSON nulo
    Quando o status é consultado
    Então o cliente deve emitir falha de integração explícita sem erro de ponteiro nulo

  @AC-037
  Cenário: Tratar status ausente
    Dado uma resposta assinada sem status reconhecido
    Quando o status é consultado
    Então o cliente deve emitir falha de status inesperado

  @AC-038
  Cenário: Exigir consentimento verdadeiro na API
    Dado um formulário de opt-in com consentimento falso
    Quando o formulário é enviado à API
    Então a API deve responder HTTP 400 sem contatar a Central

  @AC-039
  Cenário: Validar formato da conta de destino
    Dado uma solicitação com código de banco inválido
    Quando a solicitação é enviada à API
    Então a API deve responder HTTP 400 sem contatar a Central

  @AC-040
  Cenário: Validar chave no contrato público
    Dado uma solicitação válida sem header Idempotency-Key
    Quando a solicitação é enviada à API
    Então a API deve responder HTTP 400 com código CHAVE_INVALIDA

  @AC-041
  Cenário: Recusar cenário desconhecido
    Dado uma solicitação com cenário não reconhecido
    Quando o JSON é enviado à API
    Então a API deve responder HTTP 400 com código JSON_INVALIDO

  @AC-042
  Cenário: Informar operação inexistente
    Dado um identificador sem operação correspondente
    Quando o histórico dessa operação é solicitado
    Então a API deve responder HTTP 404

  @AC-043
  Cenário: Capturar consentimento explícito no navegador
    Dado o formulário de autorização ainda não consentido
    Quando o usuário marca a autorização e consulta a agenda
    Então o botão deve ser habilitado e o envio deve conter consentimento verdadeiro

  @AC-044
  Cenário: Preservar chave de envio no navegador
    Dado uma solicitação cujo resultado de rede é incerto
    Quando a mesma seleção é reenviada em outra ordem ou a sessão é relida
    Então a chave persistida deve ser a mesma

  @AC-045
  Cenário: Renovar chave quando a intenção muda
    Dado uma solicitação armazenada na sessão
    Quando o conteúdo muda ou a operação anterior é concluída
    Então uma nova chave deve ser gerada

  @AC-046
  Cenário: Apresentar falha da Central ao usuário
    Dado uma resposta de erro da API com mensagem de indisponibilidade
    Quando o frontend processa a resposta
    Então a mensagem original de indisponibilidade deve ser preservada

  @AC-047
  Cenário: Recuperar operação após reinício e impedir alteração da auditoria
    Dado uma operação aceita com protocolo persistido no PostgreSQL
    Quando todos os serviços são reiniciados e uma alteração no histórico é tentada
    Então o protocolo e os eventos anteriores devem ser preservados e a operação deve confirmar sem duplicidade e o PostgreSQL deve bloquear a alteração de auditoria

  @AC-048
  Cenário: Manter domínio e aplicação independentes de frameworks
    Dado o núcleo do backend organizado em domínio e aplicação
    Quando suas dependências de bytecode são verificadas
    Então o núcleo deve depender apenas de Java e das próprias abstrações internas

  @AC-049
  Cenário: Conduzir entradas pelas portas de casos de uso
    Dado adaptadores de entrada HTTP e scheduling
    Quando suas dependências são verificadas
    Então eles não devem depender de adaptadores de saída nem das implementações de casos de uso

  @AC-050
  Cenário: Impedir dependências de saída para implementações internas
    Dado adaptadores de saída de persistência e integração
    Quando suas dependências são verificadas
    Então eles não devem depender de adaptadores de entrada nem de serviços de aplicação ou do simulador

  @AC-051
  Cenário: Usar somente imports Java explícitos
    Dado os fontes Java de produção e testes
    Quando as declarações de import são verificadas no build
    Então nenhum import comum ou estático deve utilizar curinga

  @AC-052
  Cenário: Recusar item nulo na agenda assinada
    Dado uma resposta assinada contendo item nulo na agenda
    Quando a agenda é processada
    Então o cliente deve retornar falha explícita de integração sem NullPointerException

  @AC-053
  Cenário: Recusar identificadores duplicados na agenda
    Dado uma agenda assinada com identificadores de parcela repetidos
    Quando a agenda é processada
    Então a resposta deve ser recusada como inesperada

  @AC-054
  Cenário: Validar valores e status da agenda externa
    Dado uma agenda assinada com valor não positivo ou status desconhecido
    Quando a agenda é processada
    Então o cliente deve emitir falha explícita de integração

  @AC-055
  Cenário: Limitar retornos externos antes da persistência
    Dado uma resposta assinada com motivo de rejeição maior que o limite suportado
    Quando o status remoto é processado
    Então o cliente deve emitir falha de integração antes de tentar gravar o motivo

  @AC-056
  Cenário: Preservar a direção das dependências do domínio
    Dado o pacote de domínio do backend
    Quando as dependências do domínio são verificadas
    Então nenhuma classe de domínio deve depender da aplicação ou de camadas externas

  @AC-057
  Cenário: Confirmar portabilidade de várias parcelas
    Dado duas parcelas futuras elegíveis do mesmo beneficiário
    Quando uma única portabilidade é confirmada
    Então as duas contas devem ser atualizadas e uma única operação deve ser registrada

  @AC-058
  Cenário: Recusar seleção parcialmente inelegível sem efeitos parciais
    Dado uma seleção com parcela aberta e parcela paga
    Quando o registro da operação é solicitado
    Então nenhuma operação reserva ou evento deve ser persistido

  @AC-059
  Cenário: Reverter confirmação em falha de persistência
    Dado uma operação pendente cuja Central confirma a portabilidade
    Quando a gravação final da auditoria falha
    Então estado conta e eventos devem ser revertidos e um retry deve concluir sem duplicidade

  @AC-060
  Cenário: Recuperar protocolo após confirmação concorrente
    Dado um reenvio cuja primeira consulta idempotente ainda não encontrou operação
    Quando outra requisição aceita e confirma a mesma intenção antes do lock das parcelas
    Então o reenvio deve retornar o protocolo existente sem rejeitar a parcela já portada

  @AC-061
  Cenário: Reenviar timeout anterior ao aceite com idempotência
    Dado uma Central no cenário TIMEOUT
    Quando as duas primeiras chamadas excedem o timeout e a terceira é reenviada
    Então um protocolo único deve ser retornado e somente uma operação remota deve existir

  @AC-062
  Cenário: Exibir somente descontos futuros
    Dado uma agenda contendo parcela futura e parcela vencida
    Quando a agenda do beneficiário autorizado é consultada
    Então somente a parcela futura deve ser exibida

  @AC-063
  Cenário: Restringir agenda ao convênio consentido
    Dado um beneficiário autorizado somente para um dos convênios retornados
    Quando a agenda é consultada e uma parcela do outro convênio é solicitada
    Então a parcela sem consentimento deve ser ocultada e seu registro deve ser recusado

  @AC-064
  Cenário: Manter a projeção da auditoria imutável
    Dado uma operação construída com uma lista de eventos
    Quando a lista original ou a lista exposta pela operação é alterada
    Então os eventos da operação devem permanecer inalterados

  @AC-065
  Cenário: Tratar corpo de erro inesperado no frontend
    Dado uma resposta de erro da API cujo JSON é nulo ou não contém objeto de erro válido
    Quando o frontend processa a resposta
    Então uma mensagem de falha controlada deve ser exibida sem erro de acesso a propriedades
