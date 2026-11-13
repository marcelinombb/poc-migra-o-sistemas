# Arquitetura de referência — Integração gradual entre legado e novo sistema

A arquitetura será baseada em integração orientada a eventos, utilizando RabbitMQ como infraestrutura de mensageria. O objetivo é permitir que os sistemas evoluam independentemente, substituindo funcionalidades gradualmente, sem comprometer os processos críticos existentes.

A seguir, os principais padrões e decisões arquiteturais recomendados.

## 1. Strangler Fig — Substituição gradual do legado

Objetivo: substituir progressivamente as funcionalidades do sistema legado, sem necessidade de uma migração completa.

A estratégia consiste em implementar novas funcionalidades no sistema moderno e direcionar gradualmente as operações para ele.

- O legado continua responsável pelos processos ainda não migrados.
- O novo sistema assume funcionalidades conforme são implementadas e validadas.
- Uma camada de roteamento pode direcionar as requisições para o sistema responsável por cada funcionalidade.
- A substituição ocorre por domínio ou processo de negócio, não necessariamente por módulo inteiro.

Resultado: evolução incremental, com menor risco operacional e possibilidade de rollback durante a transição.

## 2. Publish/Subscribe — Distribuição de eventos

Objetivo: permitir que os sistemas compartilhem acontecimentos de negócio sem dependência direta entre eles.

O RabbitMQ será responsável pela distribuição dos eventos publicados.

- Cada sistema publica eventos relacionados às alterações que realiza.
- Os sistemas interessados recebem esses eventos por meio de suas próprias filas.
- Cada consumidor processa o evento conforme suas regras de negócio.
- Os sistemas não precisam conhecer a implementação interna dos outros.

Resultado: baixo acoplamento, comunicação assíncrona e possibilidade de adicionar novos consumidores sem modificar os produtores.

## 3. Transactional Outbox — Publicação confiável

Objetivo: garantir que uma alteração persistida no banco de dados seja acompanhada de sua respectiva publicação na mensageria.

O problema acontece quando o sistema salva uma alteração no banco, mas falha antes de publicar o evento no RabbitMQ.

A Outbox resolve isso utilizando a mesma transação do banco de dados:

1. O sistema executa a operação de negócio.
2. Persiste a alteração nas tabelas de negócio.
3. Registra o evento em uma tabela `outbox`.
4. Confirma a transação.
5. Um processo independente publica os eventos pendentes no RabbitMQ.
6. Após a confirmação da publicação, o evento é marcado como processado.

Resultado: evita inconsistências entre a persistência dos dados e a publicação dos eventos.

## 4. Inbox + Idempotent Consumer — Consumo confiável

Objetivo: garantir que o processamento de eventos seja seguro mesmo diante de entregas duplicadas.

O RabbitMQ pode entregar novamente uma mensagem, por exemplo, quando ocorre uma falha durante o processamento ou na confirmação do recebimento.

A combinação Inbox + Idempotência resolve esse problema:

- Cada evento possui um identificador único.
- O consumidor registra os eventos recebidos em uma tabela `inbox`.
- Antes de processar, verifica se o evento já foi tratado.
- Se já foi processado, ignora a duplicata.
- Se for novo, executa as regras de negócio e persiste os resultados.

O registro na Inbox e as alterações de negócio devem ocorrer na mesma transação.

Resultado: processamento idempotente, evitando efeitos duplicados e aumentando a confiabilidade da integração.

## 5. Anti-Corruption Layer (ACL) — Isolamento dos modelos

Objetivo: impedir que os conceitos, estruturas e particularidades de um sistema contaminem o modelo de domínio do outro.

Como os sistemas podem possuir arquiteturas, tecnologias e modelos de dados diferentes, a comunicação deve ocorrer por meio de uma camada de tradução.

- Converte eventos externos para os conceitos internos do sistema.
- Traduz identificadores, nomenclaturas e estruturas de dados.
- Aplica validações necessárias para interpretar as informações recebidas.
- Evita dependência direta entre entidades e modelos de domínio dos sistemas.

Resultado: independência arquitetural e preservação dos modelos de negócio de cada sistema.

## 6. Contrato padrão dos eventos — Padronização da comunicação

Objetivo: estabelecer um formato único de comunicação entre os sistemas, independentemente de suas tecnologias e arquiteturas.

Minha recomendação é adotar o padrão CloudEvents, que define uma estrutura comum para representar eventos.

Um evento deve possuir, no mínimo:

| Campo             | Responsabilidade                     |
| ----------------- | ------------------------------------ |
| `id`              | Identificador único do evento.       |
| `source`          | Sistema responsável pela publicação. |
| `type`            | Tipo do acontecimento de negócio.    |
| `subject`         | Entidade ou recurso relacionado.     |
| `time`            | Data e hora da ocorrência.           |
| `datacontenttype` | Formato do conteúdo.                 |
| `data`            | Dados específicos do evento.         |

Exemplo conceitual:

```
{
  "specversion": "1.0",
  "id": "evt-123456",
  "source": "sistema-legado",
  "type": "aluno.matricula.confirmada",
  "subject": "matricula/987",
  "time": "2026-11-12T18:30:00Z",
  "datacontenttype": "application/json",
  "data": {
    "matriculaId": 987,
    "alunoId": 123,
    "periodoLetivoId": 2026
  }
}
```

Resultado: contratos previsíveis, interoperabilidade e facilidade de integração entre diferentes tecnologias.

## 7. Versionamento dos contratos — Evolução compatível

Objetivo: permitir que os contratos evoluam sem interromper os sistemas que ainda utilizam versões anteriores.

Recomendo estabelecer uma política explícita de versionamento dos eventos.

- Alterações aditivas e compatíveis devem preservar o contrato existente.
- Alterações incompatíveis devem gerar uma nova versão do evento.
- Consumidores devem ignorar campos desconhecidos quando possível.
- Produtores devem manter compatibilidade durante o período de transição.
- Versões antigas devem possuir uma política de descontinuação.

Exemplo:

```
aluno.matricula.confirmada.v1
aluno.matricula.confirmada.v2
```

Uma alternativa é manter o `type` estável e versionar o schema separadamente, por exemplo, por meio de um campo `dataschema` do CloudEvents.

Resultado: evolução independente dos sistemas, sem exigir atualizações simultâneas de todos os consumidores.

## Resumo dos padrões arquiteturais

| Padrão                        | Problema que resolve                  |
| ----------------------------- | ------------------------------------- |
| Strangler Fig                 | Substituição gradual do legado.       |
| Publish/Subscribe             | Comunicação desacoplada.              |
| Transactional Outbox          | Confiabilidade na publicação.         |
| Inbox + Idempotência          | Duplicidade no processamento.         |
| Anti-Corruption Layer         | Acoplamento entre modelos de domínio. |
| Contrato padrão (CloudEvents) | Padronização da comunicação.          |
| Versionamento                 | Evolução independente dos contratos.  |

## Diretriz arquitetural final

Eu estabeleceria esses sete pontos como princípios obrigatórios da infraestrutura de integração, mas permitiria que cada equipe implementasse seus mecanismos internos conforme sua arquitetura.

Três decisões são especialmente importantes:

1. Propriedade dos dados: cada sistema é responsável pelos dados que administra. O outro sistema não deve alterar diretamente seu banco.
2. Consistência eventual: a comunicação entre sistemas ocorre de forma assíncrona. Cada sistema processa os eventos e mantém sua própria visão dos dados.
3. Contratos independentes da implementação: os eventos representam acontecimentos de negócio, não estruturas internas de tabelas ou entidades.

Com isso, a integração deixa de ser um conjunto de adaptações pontuais entre dois sistemas e passa a ser uma infraestrutura padronizada para a evolução de todo o ecossistema.
