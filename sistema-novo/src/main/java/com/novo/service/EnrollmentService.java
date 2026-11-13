package com.novo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novo.contracts.CloudEvent;
import com.novo.contracts.v1.MatriculaStatusAtualizadoV1Payload;
import com.novo.domain.Enrollment;
import com.novo.domain.OutboxMessage;
import com.novo.dto.EnrollmentResponse;
import com.novo.dto.UpdateStatusRequest;
import com.novo.repository.EnrollmentRepository;
import com.novo.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.routing-keys.matricula-status-atualizado-v1}")
    private String routingKeyStatusAtualizado;

    @Transactional
    public Enrollment saveFromAcl(Enrollment enrollment) {
        log.info("[EnrollmentService] Persistindo matrícula convertida pela ACL com legacyId: {}", enrollment.getLegacyId());
        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public EnrollmentResponse updateStatus(UUID id, UpdateStatusRequest request) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Enrollment não encontrado: " + id));

        Instant agora = Instant.now();
        enrollment.setStatus(request.getStatus().toUpperCase());
        enrollment.setUpdatedAt(agora);
        enrollment = enrollmentRepository.save(enrollment);

        // Gera evento CloudEvent v1
        UUID eventId = UUID.randomUUID();
        MatriculaStatusAtualizadoV1Payload payload = MatriculaStatusAtualizadoV1Payload.builder()
                .legacyId(enrollment.getLegacyId())
                .enrollmentId(enrollment.getId().toString())
                .status(enrollment.getStatus())
                .reason(request.getReason())
                .updatedAt(agora)
                .build();

        CloudEvent<MatriculaStatusAtualizadoV1Payload> cloudEvent = CloudEvent.<MatriculaStatusAtualizadoV1Payload>builder()
                .specversion("1.0")
                .id(eventId.toString())
                .source("sistema-novo")
                .type(routingKeyStatusAtualizado)
                .subject("enrollment/" + enrollment.getId())
                .time(agora)
                .datacontenttype("application/json")
                .data(payload)
                .build();

        // Transactional Outbox: Salva na outbox na mesma transação
        try {
            String jsonPayload = objectMapper.writeValueAsString(cloudEvent);
            OutboxMessage outbox = OutboxMessage.builder()
                    .id(eventId)
                    .eventType(routingKeyStatusAtualizado)
                    .routingKey(routingKeyStatusAtualizado)
                    .payloadJson(jsonPayload)
                    .processed(false)
                    .createdAt(agora)
                    .build();
            outboxRepository.save(outbox);
            log.info("[Outbox-Novo] Evento registrado na outbox para enrollment ID: {}", enrollment.getId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao serializar CloudEvent para Outbox", e);
        }

        return toResponse(enrollment);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> listAll() {
        return enrollmentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse findById(UUID id) {
        return enrollmentRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Enrollment não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse findByLegacyId(Long legacyId) {
        return enrollmentRepository.findByLegacyId(legacyId)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Enrollment não encontrado para legacyId: " + legacyId));
    }

    private EnrollmentResponse toResponse(Enrollment e) {
        return EnrollmentResponse.builder()
                .id(e.getId())
                .legacyId(e.getLegacyId())
                .studentId(e.getStudentId())
                .academicTerm(e.getAcademicTerm())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
