package com.novo.contracts.v1;

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
public class MatriculaStatusAtualizadoV1Payload {
    private Long legacyId;
    private String enrollmentId;
    private String status;
    private String reason;
    private Instant updatedAt;
}
