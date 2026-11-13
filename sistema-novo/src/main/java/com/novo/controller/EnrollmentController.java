package com.novo.controller;

import com.novo.dto.EnrollmentResponse;
import com.novo.dto.UpdateStatusRequest;
import com.novo.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
@Tag(name = "Enrollments (Modern)", description = "Operações de Matrícula no Sistema Moderno")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @GetMapping
    @Operation(summary = "Listar todas as matrículas no sistema moderno")
    public ResponseEntity<List<EnrollmentResponse>> listAll() {
        return ResponseEntity.ok(enrollmentService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar matrícula por UUID moderno")
    public ResponseEntity<EnrollmentResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(enrollmentService.findById(id));
    }

    @GetMapping("/legacy/{legacyId}")
    @Operation(summary = "Consultar matrícula por ID legado de correlação")
    public ResponseEntity<EnrollmentResponse> findByLegacyId(@PathVariable Long legacyId) {
        return ResponseEntity.ok(enrollmentService.findByLegacyId(legacyId));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Atualizar status da matrícula no sistema moderno",
               description = "Atualiza o status (ex: CONFIRMED, CANCELLED) e agenda a publicação de retorno ao Legado via Transactional Outbox")
    public ResponseEntity<EnrollmentResponse> updateStatus(
            @PathVariable UUID id,
            @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(enrollmentService.updateStatus(id, request));
    }
}
