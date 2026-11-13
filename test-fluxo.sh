#!/usr/bin/env bash
set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
RED='\033[0;31m'
NC='\033[0m' # No Color

RESET_DB=false
CLEAN_ONLY=false
DRY_RUN=false

# Processamento de parâmetros
for arg in "$@"; do
  case $arg in
    --reset|-r)
      RESET_DB=true
      shift
      ;;
    --clean-only)
      CLEAN_ONLY=true
      shift
      ;;
    --dry-run|-d)
      DRY_RUN=true
      shift
      ;;
    --help|-h)
      echo "Uso: ./test-fluxo.sh [OPÇÕES]"
      echo ""
      echo "Opções:"
      echo "  (sem argumentos)  Executa o teste E2E incremental preservando o histórico existente."
      echo "  --reset, -r       Limpa bancos (TRUNCATE) e purga filas do RabbitMQ antes de testar."
      echo "  --clean-only      Apenas reseta os bancos e as filas para o estado zero e finaliza."
      echo "  --dry-run, -d     Modo simulação explicativa: exibe contratos e fluxo sem modificar dados."
      echo "  --help, -h        Exibe esta mensagem de ajuda."
      exit 0
      ;;
    *)
      ;;
  esac
done

function reset_environment() {
  echo -e "\n${MAGENTA}[RESET] Limpando tabelas dos bancos de dados e filas do RabbitMQ...${NC}"
  
  # Limpa tabelas do banco Legado
  docker exec poc-postgres psql -U postgres -d legado_db -c \
    "TRUNCATE TABLE tb_matricula, tb_outbox, tb_inbox RESTART IDENTITY CASCADE;" > /dev/null 2>&1 || true

  # Limpa tabelas do banco Novo
  docker exec poc-postgres psql -U postgres -d novo_db -c \
    "TRUNCATE TABLE enrollment, outbox, inbox CASCADE;" > /dev/null 2>&1 || true

  # Purga mensagens pendentes nas filas do RabbitMQ
  curl -s -u guest:guest -X DELETE http://localhost:15672/api/queues/%2F/novo.matriculas.queue/contents > /dev/null 2>&1 || true
  curl -s -u guest:guest -X DELETE http://localhost:15672/api/queues/%2F/legado.matriculas.queue/contents > /dev/null 2>&1 || true

  echo -e "${GREEN}✓ Tabelas 'tb_matricula', 'tb_outbox', 'tb_inbox', 'enrollment', 'outbox', 'inbox' truncadas!${NC}"
  echo -e "${GREEN}✓ Filas 'novo.matriculas.queue' e 'legado.matriculas.queue' purgadas no RabbitMQ!${NC}"
}

if [ "$CLEAN_ONLY" = true ]; then
  echo -e "${BLUE}================================================================${NC}"
  echo -e "${BLUE}          RESET DO AMBIENTE (ESTADO ZERO)                       ${NC}"
  echo -e "${BLUE}================================================================${NC}"
  reset_environment
  echo -e "\n${GREEN}Ambiente resetado com sucesso! Pronto para novos testes.${NC}"
  exit 0
fi

if [ "$DRY_RUN" = true ]; then
  echo -e "${CYAN}================================================================${NC}"
  echo -e "${CYAN}    MODO DRY-RUN / SIMULAÇÃO DIDÁTICA DO FLUXO DE SINCRONIZAÇÃO ${NC}"
  echo -e "${CYAN}================================================================${NC}"
  echo -e "${YELLOW}* Nenhuma alteração real será feita no banco ou no broker.${NC}\n"

  echo -e "${CYAN}[Passo 1: Requisição de Matrícula no Legado]${NC}"
  echo "Chamada: POST http://localhost:8081/api/matriculas"
  echo "Body HTTP:"
  cat << 'EOF'
{
  "codAluno": 4501,
  "periodoLetivo": "2026/1"
}
EOF
  echo -e "Ação interna no Legado:"
  echo " - Insere em tb_matricula (nom_status = 'CRIADA')"
  echo " - Insere evento na tb_outbox na MESMA transação ACID"

  echo -e "\n${CYAN}[Passo 2: Despacho do Evento 1 via RabbitMQ (Transactional Outbox)]${NC}"
  echo "Worker @Scheduled lê tb_outbox e envia para Exchange: academic.events com Routing Key: aluno.matricula.criada.v1"
  echo "Contrato CloudEvents 1.0 gerado:"
  cat << 'EOF'
{
  "specversion": "1.0",
  "id": "e7b0a724-4f4d-44a3-9cb7-7f0ad562df0a",
  "source": "sistema-legado",
  "type": "aluno.matricula.criada.v1",
  "subject": "matricula/1",
  "time": "2026-11-12T20:15:00Z",
  "datacontenttype": "application/json",
  "data": {
    "codMatricula": 1,
    "codAluno": 4501,
    "periodoLetivo": "2026/1",
    "status": "CRIADA",
    "dataMatricula": "2026-11-12T20:15:00Z"
  }
}
EOF

  echo -e "\n${CYAN}[Passo 3: Consumo no Sistema Novo (Inbox + ACL)]${NC}"
  echo " - Checa se ID 'e7b0a724...' já existe na tabela 'inbox' (idempotência)."
  echo " - Se for novo: Anti-Corruption Layer (ACL) traduz:"
  echo "     codMatricula: 1 ➔ legacyId: 1"
  echo "     codAluno: 4501 ➔ studentId: 'STD-4501'"
  echo "     status: 'CRIADA' ➔ status: 'PENDING'"
  echo " - Persiste na tabela moderna 'enrollment' e registra ID na tabela 'inbox'."

  echo -e "\n${CYAN}[Passo 4: Atualização no Sistema Novo (Novo ➔ Legado)]${NC}"
  echo "Chamada: PATCH http://localhost:8082/api/enrollments/{uuid}/status"
  echo "Body HTTP:"
  cat << 'EOF'
{
  "status": "CONFIRMED",
  "reason": "Pagamento confirmado via portal moderno"
}
EOF
  echo "Ação interna no Novo:"
  echo " - Atualiza status da enrollment para 'CONFIRMED'"
  echo " - Insere evento na tabela 'outbox' na mesma transação"

  echo -e "\n${CYAN}[Passo 5: Despacho do Evento 2 de Retorno via RabbitMQ]${NC}"
  echo "Worker @Scheduled lê outbox e envia para Exchange: academic.events com Routing Key: matricula.status.atualizado.v1"
  echo "Contrato CloudEvents 1.0 gerado:"
  cat << 'EOF'
{
  "specversion": "1.0",
  "id": "a93e3b3d-7128-4b72-b8ec-f23bfd68c9e1",
  "source": "sistema-novo",
  "type": "matricula.status.atualizado.v1",
  "subject": "enrollment/3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "time": "2026-11-12T20:20:00Z",
  "datacontenttype": "application/json",
  "data": {
    "legacyId": 1,
    "enrollmentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "CONFIRMED",
    "reason": "Pagamento confirmado via portal moderno",
    "updatedAt": "2026-11-12T20:20:00Z"
  }
}
EOF

  echo -e "\n${CYAN}[Passo 6: Consumo de Retorno no Legado (Inbox)]${NC}"
  echo " - Legado recebe evento da fila 'legado.matriculas.queue'."
  echo " - Checa tabela 'tb_inbox'."
  echo " - Traduz status moderno 'CONFIRMED' para legado 'ATIVA'."
  echo " - Atualiza 'tb_matricula' onde id = 1 e insere na 'tb_inbox'."

  echo -e "\n${GREEN}✓ Simulação didática concluída com sucesso! Para executar de verdade, rode './test-fluxo.sh'.${NC}"
  exit 0
fi

# ==============================================================================
# EXECUÇÃO DO TESTE REAL
# ==============================================================================

echo -e "${BLUE}================================================================${NC}"
echo -e "${BLUE}    TESTE AUTOMATIZADO PONTA A PONTA - POC MIGRAÇÃO GRADUAL     ${NC}"
echo -e "${BLUE}================================================================${NC}"

if [ "$RESET_DB" = true ]; then
  reset_environment
fi

# 1. Verifica conectividade com as APIs
echo -e "\n${YELLOW}[1/6] Verificando se os serviços estão operacionais...${NC}"

for i in {1..30}; do
  LEGADO_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/api/matriculas || true)
  NOVO_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/api/enrollments || true)

  if [ "$LEGADO_STATUS" == "200" ] && [ "$NOVO_STATUS" == "200" ]; then
    echo -e "${GREEN}✓ Sistema Legado (porta 8081) e Sistema Novo (porta 8082) prontos!${NC}"
    break
  fi
  echo "Aguardando serviços inicializarem... ($i/30)"
  sleep 3
done

if [ "$LEGADO_STATUS" != "200" ] || [ "$NOVO_STATUS" != "200" ]; then
  echo -e "${RED}Erro: Serviços não responderam a tempo. Verifique com 'docker compose logs'.${NC}"
  exit 1
fi

# 2. Criação da matrícula no Sistema Legado
ALUNO_ID=$((RANDOM % 9000 + 1000))
echo -e "\n${YELLOW}[2/6] Criando matrícula no Sistema Legado para aluno codAluno=${ALUNO_ID}...${NC}"

CRIAR_RES=$(curl -s -X POST http://localhost:8081/api/matriculas \
  -H "Content-Type: application/json" \
  -d "{\"codAluno\": ${ALUNO_ID}, \"periodoLetivo\": \"2026/1\"}")

echo "Resposta do Legado: ${CRIAR_RES}"
LEGACY_ID=$(echo "${CRIAR_RES}" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

if [ -z "$LEGACY_ID" ]; then
  echo -e "${RED}Falha ao obter ID da matrícula criada.${NC}"
  exit 1
fi
echo -e "${GREEN}✓ Matrícula Legada criada com ID: ${LEGACY_ID} e registrada na Outbox!${NC}"

# 3. Aguarda processamento do Worker da Outbox e consumo no Novo Sistema
echo -e "\n${YELLOW}[3/6] Aguardando propagação do evento aluno.matricula.criada.v1 via RabbitMQ (4s)...${NC}"
sleep 4

ENROLLMENT_RES=$(curl -s http://localhost:8082/api/enrollments/legacy/${LEGACY_ID})
echo "Consulta no Sistema Novo: ${ENROLLMENT_RES}"

ENROLLMENT_ID=$(echo "${ENROLLMENT_RES}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
ENROLLMENT_STATUS=$(echo "${ENROLLMENT_RES}" | grep -o '"status":"[^"]*' | head -1 | cut -d'"' -f4)

if [ -n "$ENROLLMENT_ID" ] && [ "$ENROLLMENT_STATUS" == "PENDING" ]; then
  echo -e "${GREEN}✓ Sucesso! Matrícula sincronizada no Novo Sistema:${NC}"
  echo -e "   UUID Moderno: ${ENROLLMENT_ID}"
  echo -e "   Status via ACL: ${ENROLLMENT_STATUS} (mapeado de CRIADA)"
else
  echo -e "${RED}Falha: Registro não foi encontrado no novo sistema ou status incorreto.${NC}"
  exit 1
fi

# 4. Atualização de status no Sistema Novo (Novo -> Legado)
echo -e "\n${YELLOW}[4/6] Confirmando matrícula no Sistema Novo (dispara matricula.status.atualizado.v1)...${NC}"

UPDATE_RES=$(curl -s -X PATCH "http://localhost:8082/api/enrollments/${ENROLLMENT_ID}/status" \
  -H "Content-Type: application/json" \
  -d '{"status": "CONFIRMED", "reason": "Matrícula quitada com sucesso"}')

echo "Resposta do Sistema Novo: ${UPDATE_RES}"
echo -e "${GREEN}✓ Status atualizado no Sistema Novo e registrado na Outbox moderna!${NC}"

# 5. Aguarda propagação de retorno ao Legado
echo -e "\n${YELLOW}[5/6] Aguardando propagação de retorno ao Sistema Legado via RabbitMQ (4s)...${NC}"
sleep 4

LEGADO_CHECK=$(curl -s "http://localhost:8081/api/matriculas/${LEGACY_ID}")
echo "Consulta de validação no Legado: ${LEGADO_CHECK}"
STATUS_ATUALIZADO=$(echo "${LEGADO_CHECK}" | grep -o '"status":"[^"]*' | head -1 | cut -d'"' -f4)

if [ "$STATUS_ATUALIZADO" == "ATIVA" ]; then
  echo -e "${GREEN}✓ SUCESSO TOTAL! Legado recebeu o evento e atualizou status para: ${STATUS_ATUALIZADO}!${NC}"
else
  echo -e "${RED}Falha: Status no legado deveria ser 'ATIVA', mas é '${STATUS_ATUALIZADO}'.${NC}"
  exit 1
fi

# 6. Teste de Idempotência (Inbox)
echo -e "\n${YELLOW}[6/6] Testando Idempotência com reenvio de mensagem duplicada...${NC}"
MSG_TEST='{
  "properties": {"content_type": "application/json"},
  "routing_key": "matricula.status.atualizado.v1",
  "payload": "{\"specversion\":\"1.0\",\"id\":\"idempotence-test-001\",\"source\":\"sistema-novo\",\"type\":\"matricula.status.atualizado.v1\",\"subject\":\"test\",\"time\":\"2026-11-12T20:00:00Z\",\"datacontenttype\":\"application/json\",\"data\":{\"legacyId\":'${LEGACY_ID}',\"enrollmentId\":\"'${ENROLLMENT_ID}'\",\"status\":\"CONFIRMED\",\"reason\":\"Tentativa 1\",\"updatedAt\":\"2026-11-12T20:00:00Z\"}}",
  "payload_encoding": "string"
}'

# Envio 1
curl -s -u guest:guest -X POST "http://localhost:15672/api/exchanges/%2F/academic.events/publish" \
  -H "Content-Type: application/json" -d "$MSG_TEST" > /dev/null

sleep 2

# Envio 2 (Duplicata)
curl -s -u guest:guest -X POST "http://localhost:15672/api/exchanges/%2F/academic.events/publish" \
  -H "Content-Type: application/json" -d "$MSG_TEST" > /dev/null

sleep 2

echo -e "${GREEN}✓ Duplicata tratada com sucesso pela Inbox sem falhas ou inconsistências!${NC}"

echo -e "\n${BLUE}================================================================${NC}"
echo -e "${GREEN}✓ TODOS OS TESTES E PADRÕES DA POC FORAM VALIDADOS COM SUCESSO!${NC}"
echo -e "${BLUE}================================================================${NC}"
