package br.com.legado.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dados para criação de uma matrícula no sistema legado")
public class CriarMatriculaRequest {

    @Schema(description = "Código numérico do aluno", example = "4501")
    private Long codAluno;

    @Schema(description = "Período letivo da matrícula", example = "2026/1")
    private String periodoLetivo;
}
