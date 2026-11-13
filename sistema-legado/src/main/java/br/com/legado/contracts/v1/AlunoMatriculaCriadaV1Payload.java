package br.com.legado.contracts.v1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlunoMatriculaCriadaV1Payload {
    private Long codMatricula;
    private Long codAluno;
    private String periodoLetivo;
    private String status;
    private Instant dataMatricula;
}
