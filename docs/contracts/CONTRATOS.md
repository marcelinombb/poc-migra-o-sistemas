# Governança e Catálogo de Contratos de Eventos

Este documento estabelece as regras de versionamento, ciclo de vida e os contratos formais dos eventos trocados entre o **Sistema Legado** e o **Sistema Novo**.

---

## 1. Diretrizes de Versionamento e Evolução

Seguindo as recomendações da arquitetura de referência ([poc.md](../../poc.md)), adotamos o padrão **CloudEvents 1.0** com regras de versionamento semântico de contratos:

### 1.1 Convenção de Nomenclatura do `type`
O campo `type` do CloudEvent segue o formato hierárquico:
```
<dominio>.<entidade/contexto>.<acao>.<versao>
```
Exemplos:
- `aluno.matricula.criada.v1`
- `matricula.status.atualizado.v1`

### 1.2 Regras de Compatibilidade
- **Mudanças Aditivas (Compatíveis com versões anteriores):**
  - Adição de campos opcionais no payload `data`.
  - Consumidores **devem ignorar** campos desconhecidos (tolerância a campos extras).
  - Mantém-se a mesma versão (`.v1`).
- **Mudanças Incompatíveis (Breaking Changes):**
  - Remoção de campos existentes ou alteração de tipo/significado de um campo.
  - Exige a criação de uma nova versão do contrato (ex: `.v2`).
  - Durante o período de transição, os sistemas produtores podem publicar em `.v1` e `.v2` em paralelo até a descontinuação da versão anterior.

---

## 2. Catálogo de Eventos da POC

### 2.1 Evento: `aluno.matricula.criada.v1`
- **Origem (Source):** `sistema-legado`
- **Destino Principal:** `sistema-novo`
- **Exchange:** `academic.events` (Topic)
- **Routing Key:** `aluno.matricula.criada.v1`
- **Fila do Consumidor:** `novo.matriculas.queue`
- **Schema JSON:** [schemas/aluno.matricula.criada.v1.json](./schemas/aluno.matricula.criada.v1.json)

#### Exemplo do Envelope CloudEvent:
```json
{
  "specversion": "1.0",
  "id": "e7b0a724-4f4d-44a3-9cb7-7f0ad562df0a",
  "source": "sistema-legado",
  "type": "aluno.matricula.criada.v1",
  "subject": "matricula/1001",
  "time": "2026-11-12T20:15:00Z",
  "datacontenttype": "application/json",
  "data": {
    "codMatricula": 1001,
    "codAluno": 4500,
    "periodoLetivo": "2026/1",
    "status": "CRIADA",
    "dataMatricula": "2026-11-12T20:15:00Z"
  }
}
```

---

### 2.2 Evento: `matricula.status.atualizado.v1`
- **Origem (Source):** `sistema-novo`
- **Destino Principal:** `sistema-legado`
- **Exchange:** `academic.events` (Topic)
- **Routing Key:** `matricula.status.atualizado.v1`
- **Fila do Consumidor:** `legado.matriculas.queue`
- **Schema JSON:** [schemas/matricula.status.atualizado.v1.json](./schemas/matricula.status.atualizado.v1.json)

#### Exemplo do Envelope CloudEvent:
```json
{
  "specversion": "1.0",
  "id": "a93e3b3d-7128-4b72-b8ec-f23bfd68c9e1",
  "source": "sistema-novo",
  "type": "matricula.status.atualizado.v1",
  "subject": "enrollment/3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "time": "2026-11-12T20:20:00Z",
  "datacontenttype": "application/json",
  "data": {
    "legacyId": 1001,
    "enrollmentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "CONFIRMED",
    "reason": "Pagamento confirmado via portal moderno",
    "updatedAt": "2026-11-12T20:20:00Z"
  }
}
```
