package br.com.legado.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tb_matricula")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Matricula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_aluno", nullable = false)
    private Long codAluno;

    @Column(name = "num_periodo", nullable = false, length = 20)
    private String numPeriodo;

    @Column(name = "nom_status", nullable = false, length = 30)
    private String nomStatus;

    @Column(name = "dat_criacao", nullable = false)
    private Instant datCriacao;

    @Column(name = "dat_atualizacao")
    private Instant datAtualizacao;
}
