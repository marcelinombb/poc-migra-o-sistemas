package br.com.legado.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tb_inbox")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxMessage {

    @Id
    @Column(name = "id_evento", length = 100)
    private String idEvento;

    @Column(name = "tipo_evento", nullable = false)
    private String tipoEvento;

    @Column(name = "origem", nullable = false)
    private String origem;

    @Column(name = "dat_processamento", nullable = false)
    private Instant datProcessamento;
}
