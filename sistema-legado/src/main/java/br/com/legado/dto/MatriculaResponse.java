package br.com.legado.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dados de retorno de uma matrícula no sistema legado")
public class MatriculaResponse {
    private Long id;
    private Long codAluno;
    private String periodoLetivo;
    private String status;
    private Instant dataCriacao;
    private Instant dataAtualizacao;
}
