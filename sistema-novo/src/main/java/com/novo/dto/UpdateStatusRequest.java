package com.novo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requisição para atualização de status da matrícula")
public class UpdateStatusRequest {

    @Schema(description = "Novo status desejado", example = "CONFIRMED", allowableValues = {"CONFIRMED", "CANCELLED"})
    private String status;

    @Schema(description = "Justificativa da alteração", example = "Pagamento aprovado via cartão de crédito")
    private String reason;
}
