package br.com.legado.service;

import br.com.legado.consumer.strategy.EventHandlerStrategy;
import br.com.legado.consumer.strategy.EventStrategyRegistry;
import br.com.legado.contracts.CloudEvent;
import br.com.legado.domain.InboxMessage;
import br.com.legado.repository.InboxRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/**
 * Listener da fila RabbitMQ do Sistema Legado.
 * Aplica o padrão Inbox + Idempotência e despacha a execução para a Strategy correspondente.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InboxConsumerListener {

    private final InboxRepository inboxRepository;
    private final EventStrategyRegistry strategyRegistry;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${app.rabbitmq.queues.legado-matriculas}")
    @Transactional
    public void onMessage(Message rawMessage) {
        try {
            String messageJson = new String(rawMessage.getBody(), StandardCharsets.UTF_8);
            CloudEvent<JsonNode> event = objectMapper.readValue(
                    messageJson,
                    new TypeReference<CloudEvent<JsonNode>>() {}
            );

            log.info("[Inbox-Legado] Recebido CloudEvent: id={}, type={}, subject={}",
                    event.getId(), event.getType(), event.getSubject());

            // 1. Verificação Idempotente na Inbox
            if (inboxRepository.existsById(event.getId())) {
                log.warn("[Inbox-Legado] Mensagem duplicada detectada! ID: {}. Descartando sem reprocessar.", event.getId());
                return;
            }

            // 2. Roteamento via Padrão Strategy
            Optional<EventHandlerStrategy> strategyOpt = strategyRegistry.getStrategy(event.getType());
            if (!strategyOpt.isPresent()) {
                log.warn("[Inbox-Legado] Nenhuma estratégia registrada para o evento '{}'. Mensagem será descartada.", event.getType());
                return;
            }

            // 3. Execução da Estratégia de Negócio Dedicada
            strategyOpt.get().handle(event.getData(), event.getId());

            // 4. Registro Atômico na Inbox
            InboxMessage inbox = InboxMessage.builder()
                    .idEvento(event.getId())
                    .tipoEvento(event.getType())
                    .origem(event.getSource())
                    .datProcessamento(Instant.now())
                    .build();
            inboxRepository.save(inbox);

            log.info("[Inbox-Legado] Evento id={} processado e registrado na Inbox com sucesso.", event.getId());

        } catch (Exception e) {
            log.error("[Inbox-Legado] Erro ao consumir mensagem: {}", e.getMessage(), e);
            throw new RuntimeException("Falha no consumo do evento", e);
        }
    }
}
