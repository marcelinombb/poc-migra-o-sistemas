package br.com.legado.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tb_outbox")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxMessage {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tipo_evento", nullable = false)
    private String tipoEvento;

    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    @Column(name = "conteudo_json", nullable = false, columnDefinition = "TEXT")
    private String conteudoJson;

    @Column(name = "processado", nullable = false)
    private boolean processado;

    @Column(name = "dat_criacao", nullable = false)
    private Instant datCriacao;

    @Column(name = "dat_processamento")
    private Instant datProcessamento;
}
