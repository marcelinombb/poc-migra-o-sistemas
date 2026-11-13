package com.novo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Representação moderna de uma Matrícula (Enrollment)")
public class EnrollmentResponse {
    private UUID id;
    private Long legacyId;
    private String studentId;
    private String academicTerm;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
