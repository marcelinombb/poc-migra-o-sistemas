package br.com.legado.consumer.strategy;

import br.com.legado.contracts.v1.MatriculaStatusAtualizadoV1Payload;
import br.com.legado.service.MatriculaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Estratégia dedicada ao tratamento do evento 'matricula.status.atualizado.v1'.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MatriculaStatusAtualizadoStrategy implements EventHandlerStrategy {

    private final MatriculaService matriculaService;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.routing-keys.matricula-status-atualizado-v1:matricula.status.atualizado.v1}")
    private String supportedEventType;

    @Override
    public String getSupportedEventType() {
        return supportedEventType;
    }

    @Override
    public void handle(JsonNode eventData, String eventId) {
        try {
            MatriculaStatusAtualizadoV1Payload payload = objectMapper.treeToValue(
                    eventData,
                    MatriculaStatusAtualizadoV1Payload.class
            );

            log.info("[Strategy-Legado] Processando atualização de status para legacyId={}, eventId={}",
                    payload.getLegacyId(), eventId);

            String statusLegado = traduzirStatusNovoParaLegado(payload.getStatus());
            matriculaService.atualizarStatus(payload.getLegacyId(), statusLegado);

            log.info("[Strategy-Legado] Matrícula legada {} atualizada com sucesso para status: {}",
                    payload.getLegacyId(), statusLegado);

        } catch (Exception e) {
            log.error("[Strategy-Legado] Erro ao processar evento {}: {}", supportedEventType, e.getMessage(), e);
            throw new RuntimeException("Falha na execução da estratégia para: " + supportedEventType, e);
        }
    }

    private String traduzirStatusNovoParaLegado(String statusModerno) {
        if ("CONFIRMED".equalsIgnoreCase(statusModerno)) {
            return "ATIVA";
        } else if ("CANCELLED".equalsIgnoreCase(statusModerno)) {
            return "CANCELADA";
        }
        return "PENDENTE";
    }
}
