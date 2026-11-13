# POC de Migração Gradual de Sistemas (Strangler Fig)

Esta Prova de Conceito (POC) implementa e valida os padrões de integração orientada a eventos descritos em [poc.md](poc.md) para permitir a coexistência e substituição gradual de um sistema legado sem interrupção de serviço.

---

## 1. Padrões Arquiteturais Implementados

| Padrão | Onde está implementado |
| :--- | :--- |
| **Strangler Fig** | Convivência entre **Sistema Legado** (Java 8 + Spring Boot 2.7) e **Sistema Novo** (Java 21 + Spring Boot 3.3). |
| **Publish/Subscribe** | Broker **RabbitMQ** com Exchange do tipo `Topic` (`academic.events`) e filas dedicadas para cada consumidor. |
| **Transactional Outbox** | Tabelas `tb_outbox` (Legado) e `outbox` (Novo). Gravação na mesma transação atômica do negócio e publicação via worker interno `@Scheduled`. |
| **Inbox + Idempotência** | Tabelas `tb_inbox` (Legado) e `inbox` (Novo). Deduplicação de eventos por ID único antes do processamento. |
| **Strategy Pattern (Consumer)** | Classes `EventStrategyRegistry` e `EventHandlerStrategy` em ambos os sistemas. O roteamento por `type` de evento é desacoplado do listener, viabilizando escalabilidade sem violação do Open/Closed Principle. |
| **Anti-Corruption Layer (ACL)** | Classe `AcademicAclService` no Novo Sistema, traduzindo termos e IDs legados (`codMatricula`, `codAluno`) para o domínio moderno (`legacyId`, `studentId`). |
| **Contrato CloudEvents 1.0** | Envelope padronizado (`specversion`, `id`, `source`, `type`, `time`, `data`). |
| **Versionamento de Contratos** | Contratos tipados e versionados (`aluno.matricula.criada.v1` e `matricula.status.atualizado.v1`) com schemas JSON Schema formais em [docs/contracts/CONTRATOS.md](docs/contracts/CONTRATOS.md). |

---

## 2. Eventos e Contratos Versionados

Os contratos estão formalmente documentados e versionados com JSON Schema:
- **Catálogo de Governança:** [docs/contracts/CONTRATOS.md](docs/contracts/CONTRATOS.md)
- **Evento 1 (`Legado ➔ Novo`):** [`aluno.matricula.criada.v1`](docs/contracts/schemas/aluno.matricula.criada.v1.json)
- **Evento 2 (`Novo ➔ Legado`):** [`matricula.status.atualizado.v1`](docs/contracts/schemas/matricula.status.atualizado.v1.json)

---

## 3. Topologia dos Serviços

```
                        ┌────────────────────────────────────────────────────────┐
                        │              RabbitMQ (academic.events)                │
                        │                 Topic Exchange                         │
                        └──────────┬──────────────────────────────▲──────────────┘
                                   │                              │
         aluno.matricula.criada.v1 │    matricula.status.atualizado.v1
                                   │                              │
                                   ▼                              │
┌──────────────────────────────────────────────┐  ┌───────────────┴──────────────────────────────┐
│       Sistema Legado (:8081)                 │  │       Sistema Novo (:8082)                   │
│   Java 8 / Spring Boot 2.7                   │  │   Java 21 / Spring Boot 3.3                  │
│                                              │  │                                              │
│ - Tabela: tb_matricula                       │  │ - ACL (Anti-Corruption Layer)                │
│ - Tabela: tb_outbox (Worker interno)         │  │ - Tabela: enrollment                         │
│ - Tabela: tb_inbox (Idempotência)            │  │ - Tabela: outbox (Worker interno)            │
│ - Swagger 2: /swagger-ui.html                │  │ - Tabela: inbox (Idempotência)               │
│                                              │  │ - OpenAPI 3 / Swagger: /swagger-ui.html      │
│ Banco: PostgreSQL (legado_db)                │  │ Banco: PostgreSQL (novo_db)                  │
└──────────────────────────────────────────────┘  └──────────────────────────────────────────────┘
```

---

## 4. Entendendo os Conceitos de Mensageria (RabbitMQ)

No RabbitMQ, os sistemas **nunca publicam mensagens diretamente em uma fila**. O modelo funciona exatamente como uma **central de distribuição postal / agência de correios**:

```
[Produtor] ──(publica com Routing Key)──> [Exchange] ──(Binding)──> [Queue] ──> [Consumidor]
```

Abaixo está o papel de cada configuração utilizada no `application.yml`:

```yaml
app:
  rabbitmq:
    exchange: academic.events
    queues:
      novo-matriculas: novo.matriculas.queue
    routing-keys:
      matricula-criada-v1: aluno.matricula.criada.v1
      matricula-status-atualizado-v1: matricula.status.atualizado.v1
```

### 1. `exchange: academic.events` (A Central de Triagem Postal)
* **O que é:** A **Exchange** é a porta de entrada única de mensagens no RabbitMQ. Ela recebe os pacotes do remetente e decide para quais caixas de correio (filas) deve encaminhá-los.
* **Tipo utilizado:** **`Topic`**. Permite roteamento dinâmico baseado em categorias com pontos (ex: `dominio.entidade.acao.versao`).
* **Benefício:** O emissor (Legado ou Novo) não precisa conhecer a infraestrutura de quem vai consumir; ele apenas posta o evento na exchange `academic.events`.

### 2. `queues: novo.matriculas.queue` (A Caixa de Correio Privada)
* **O que é:** A **Queue (Fila)** é onde as mensagens ficam retidas e persistidas até que o consumidor esteja disponível para processá-las.
* **Isolamento de Consumidor:** No padrão *Publish/Subscribe*, cada sistema possui sua **própria fila** (`novo.matriculas.queue` para o Sistema Novo e `legado.matriculas.queue` para o Legado).
* **Escalabilidade:** Se no futuro um terceiro sistema (ex: *Data Lake* ou *Notificações Push*) precisar saber das matrículas, basta plugar uma nova fila na mesma exchange, sem encostar no código existente.

### 3. `routing-keys:` (A Etiqueta de Endereçamento do Pacote)
A **Routing Key** é a "etiqueta de assunto" que o produtor anexa ao evento. É através dela que a Exchange sabe para qual fila enviar cada mensagem através das regras de amarração (**Bindings**).

| Configuração | Valor da Chave | Quem Publica | Quem Consome | Significado |
| :--- | :--- | :--- | :--- | :--- |
| `matricula-criada-v1` | `aluno.matricula.criada.v1` | **Sistema Legado** | **Sistema Novo** | Uma nova matrícula foi inserida pelo processo legado. |
| `matricula-status-atualizado-v1` | `matricula.status.atualizado.v1` | **Sistema Novo** | **Sistema Legado** | O status da matrícula foi alterado (ex: confirmado/pago) no novo sistema. |

### Fluxo Prático Passo a Passo (Exemplo: Criação de Matrícula)

1. **Remetente posta:** O Sistema Legado salva a matrícula no banco e seu worker despacha o CloudEvent para a Exchange `academic.events` com a etiqueta `aluno.matricula.criada.v1`.
2. **Central faz a triagem:** A Exchange analisa as regras (*Bindings*) cadastradas e verifica:
   > *"A fila `novo.matriculas.queue` se inscreveu para receber mensagens com o padrão `aluno.matricula.criada.v1`."*
3. **Depósito na caixa:** A mensagem é depositada na fila `novo.matriculas.queue`.
4. **Destinatário retira:** O Sistema Novo consome a mensagem da sua fila, verifica na tabela de `inbox` (evitando duplicatas), traduz via **ACL** e persiste no banco moderno.

---

## 5. Escalabilidade do Consumidor: Padrão Strategy + Registry

À medida que o projeto cresce e dezenas de novos eventos de negócio passam a trafegar entre o Legado e o Novo Sistema (ex: `aluno.dados.atualizados.v1`, `matricula.trancada.v1`, `disciplina.inscrita.v1`), uma implementação tradicional de listener consumidor rapidamente se tornaria uma **God Class** cheia de `if/else` ou `switch/case`, violando princípios do SOLID:
- **Violação de SRP (Single Responsibility):** O listener cuidaria da fila, da idempotência da Inbox e de dezenas de regras de negócio ao mesmo tempo.
- **Violação de OCP (Open/Closed Principle):** A cada novo evento seria necessário alterar e retestar uma classe crítica já existente em produção.

### Arquitetura Desacoplada Implementada

Para resolver isso, desacoplamos a **Infraestrutura/Idempotência** da **Regra de Negócio do Evento** utilizando os padrões **Strategy** e **Registry**:

```mermaid
flowchart TD
    Queue["RabbitMQ Queue"] --> Listener["InboxConsumerListener<br/>(Infraestrutura + Inbox Idempotente)"]
    Listener --> Registry["EventStrategyRegistry<br/>(Roteador Dinâmico)"]
    
    subgraph Strategies ["EventHandlerStrategy (@Component)"]
        S1["AlunoMatriculaCriadaStrategy<br/>type: 'aluno.matricula.criada.v1'"]
        S2["Futura: AlunoDadosAtualizadosStrategy<br/>type: 'aluno.dados.atualizados.v1'"]
        S3["Futura: MatriculaTrancadaStrategy<br/>type: 'matricula.trancada.v1'"]
    end
    
    Registry -->|Busca por event.getType()| S1
    Registry -.-> S2
    Registry -.-> S3
    S1 --> ACL["Anti-Corruption Layer (ACL)"]
    ACL --> Service["EnrollmentService / Negócio"]
```

### Papel de Cada Componente

1. **`InboxConsumerListener` (Guardião da Infraestrutura):**
   - Recebe a mensagem bruta da fila RabbitMQ.
   - Extrai o envelope CloudEvent genérico (`specversion`, `id`, `type`, `time`, etc.).
   - Executa a verificação na tabela `inbox` (se o ID já foi processado, descarta sem reprocessar).
   - Localiza a Strategy correspondente no Registry e delega o processamento.
   - Registra o evento na `inbox` na mesma transação atômica.
   - **Ele não conhece regras de negócio, ACL ou entidades de domínio.**

2. **`EventStrategyRegistry` (Roteador Automático):**
   - Injeta automaticamente pelo Spring todas as classes que implementam `EventHandlerStrategy`.
   - Indexa as estratégias em um mapa pela chave retornada por `getSupportedEventType()`.

3. **`EventHandlerStrategy` (Regra de Negócio Dedicada):**
   - Cada evento possui sua própria classe isolada (ex: `AlunoMatriculaCriadaStrategy` no Novo e `MatriculaStatusAtualizadoStrategy` no Legado).
   - Desserializa apenas o payload específico e executa a operação necessária (chamar a ACL, atualizar status, etc.).

### Como Adicionar um Novo Evento em Produção (OCP)
Para plugar o tratamento de um novo evento (ex: `aluno.dados.atualizados.v1`), o desenvolvedor **não altera nenhuma linha de código existente**:
1. Cria a classe `AlunoDadosAtualizadosStrategy` anotada com `@Component` implementando `EventHandlerStrategy`.
2. O Spring e o `EventStrategyRegistry` conectam a nova estratégia automaticamente ao inicializar.

---

## 6. Como Executar

### Pré-requisitos
- Docker e Docker Compose instalados.

### Subindo os containers
Na raiz do projeto, execute:
```bash
docker compose up --build
```

O compose iniciará:
1. **PostgreSQL** (`:5432`) - com bancos `legado_db` e `novo_db` criados automaticamente.
2. **RabbitMQ** (`:5672` AMQP e `:15672` Painel de Gestão).
3. **Sistema Legado** (`:8081`).
4. **Sistema Novo** (`:8082`).

---

## 7. Formas de Teste e Demonstração

### Opção A: Arquivo de Requisições HTTP + RabbitMQ UI
- Abra o arquivo [requests.http](requests.http) no VS Code (extensão REST Client) ou no IntelliJ IDEA.
- Execute as requisições em sequência:
  1. `POST /api/matriculas` no Legado.
  2. `GET /api/enrollments/legacy/1` no Novo Sistema para ver a sincronização.
  3. `PATCH /api/enrollments/{uuid}/status` no Novo Sistema confirmando a matrícula.
  4. `GET /api/matriculas/1` no Legado para validar a atualização de volta para `ATIVA`.
- Painel Web do RabbitMQ: [http://localhost:15672](http://localhost:15672) (usuário: `guest`, senha: `guest`).

### Opção B: Script de Teste Automatizado Ponta a Ponta
Com os containers em execução, rode:
```bash
./test-fluxo.sh                  # Execução padrão (incremental)
./test-fluxo.sh --reset          # Reseta os bancos (TRUNCATE) e filas antes de testar
./test-fluxo.sh --dry-run        # Modo simulação didática (não altera nada)
./test-fluxo.sh --clean-only     # Apenas reseta bancos e filas para o estado zero
```

O script em modo real:
1. Reseta os bancos e filas se `--reset` for informado.
2. Valida se os serviços estão operacionais.
3. Cria uma matrícula no Sistema Legado (salva em `tb_matricula` e `tb_outbox`).
4. Aguarda o worker da Outbox publicar e RabbitMQ entregar.
5. Valida que a **ACL** do Sistema Novo traduziu os dados e salvou como `PENDING`.
6. Atualiza o status para `CONFIRMED` no Sistema Novo (salva em `outbox`).
7. Valida que o Sistema Legado recebeu o retorno e alterou para `ATIVA`.
8. Envia duplicatas via RabbitMQ Management API para validar a **Inbox (Idempotência)**.

### Opção C: Documentação Interativa Swagger / OpenAPI
- **Swagger UI do Sistema Legado (Java 8 / Spring Boot 2.7):**
  [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- **Swagger UI do Sistema Novo (Java 21 / Spring Boot 3.3):**
  [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html) (ou `/swagger-ui/index.html`)
# poc-migra-o-sistemas
