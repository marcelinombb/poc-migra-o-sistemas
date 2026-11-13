package com.novo.consumer.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novo.acl.AcademicAclService;
import com.novo.contracts.v1.AlunoMatriculaCriadaV1Payload;
import com.novo.domain.Enrollment;
import com.novo.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Estratégia dedicada ao tratamento do evento 'aluno.matricula.criada.v1'.
 * Isola a conversão via ACL e a persistência no EnrollmentService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlunoMatriculaCriadaStrategy implements EventHandlerStrategy {

    private final AcademicAclService academicAclService;
    private final EnrollmentService enrollmentService;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.routing-keys.matricula-criada-v1:aluno.matricula.criada.v1}")
    private String supportedEventType;

    @Override
    public String getSupportedEventType() {
        return supportedEventType;
    }

    @Override
    public void handle(JsonNode eventData, String eventId) {
        try {
            AlunoMatriculaCriadaV1Payload payload = objectMapper.treeToValue(eventData, AlunoMatriculaCriadaV1Payload.class);
            log.info("[Strategy-Novo] Processando criação de matrícula legada codMatricula={}, eventId={}",
                    payload.getCodMatricula(), eventId);

            // 1. Anti-Corruption Layer (ACL)
            Enrollment enrollment = academicAclService.translateLegacyEnrollment(payload);

            // 2. Persistência do domínio moderno
            enrollmentService.saveFromAcl(enrollment);

            log.info("[Strategy-Novo] Enrollment criado com sucesso via ACL. ID: {}, LegacyID: {}",
                    enrollment.getId(), enrollment.getLegacyId());

        } catch (Exception e) {
            log.error("[Strategy-Novo] Erro ao processar payload do evento {}: {}", supportedEventType, e.getMessage(), e);
            throw new RuntimeException("Falha na execução da estratégia para: " + supportedEventType, e);
        }
    }
}
