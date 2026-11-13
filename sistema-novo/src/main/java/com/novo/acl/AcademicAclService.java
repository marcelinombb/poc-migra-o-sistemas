package com.novo.acl;

import com.novo.contracts.v1.AlunoMatriculaCriadaV1Payload;
import com.novo.domain.Enrollment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Anti-Corruption Layer (ACL)
 * Responsável por traduzir o modelo de dados e semântica do Legado para o modelo de domínio do Novo Sistema,
 * isolando as dependências conceituais e protegendo o domínio moderno contra contaminações conceituais legadas.
 */
@Component
@Slf4j
public class AcademicAclService {

    public Enrollment translateLegacyEnrollment(AlunoMatriculaCriadaV1Payload legacyPayload) {
        log.info("[ACL] Traduzindo matrícula legada codMatricula={} para entidade de domínio moderna", legacyPayload.getCodMatricula());

        // Tradução de identificador legado (Long) para formato de estudante no novo sistema (ex: prefixo de tenant/uuid)
        String modernStudentId = "STD-" + legacyPayload.getCodAluno();

        // Tradução de status legado para status do domínio moderno
        String modernStatus = mapLegacyStatus(legacyPayload.getStatus());

        return Enrollment.builder()
                .id(UUID.randomUUID())
                .legacyId(legacyPayload.getCodMatricula())
                .studentId(modernStudentId)
                .academicTerm(legacyPayload.getPeriodoLetivo())
                .status(modernStatus)
                .createdAt(legacyPayload.getDataMatricula() != null ? legacyPayload.getDataMatricula() : Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private String mapLegacyStatus(String legacyStatus) {
        if ("CRIADA".equalsIgnoreCase(legacyStatus)) {
            return "PENDING";
        } else if ("ATIVA".equalsIgnoreCase(legacyStatus)) {
            return "CONFIRMED";
        } else if ("CANCELADA".equalsIgnoreCase(legacyStatus)) {
            return "CANCELLED";
        }
        return "PENDING";
    }
}
