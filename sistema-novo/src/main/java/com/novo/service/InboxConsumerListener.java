package com.novo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novo.consumer.strategy.EventHandlerStrategy;
import com.novo.consumer.strategy.EventStrategyRegistry;
import com.novo.contracts.CloudEvent;
import com.novo.domain.InboxMessage;
import com.novo.repository.InboxRepository;
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
 * Listener da fila RabbitMQ do Sistema Novo.
 * Aplica o padrão Inbox + Idempotência e despacha a execução para a Strategy correspondente,
 * garantindo baixo acoplamento e conformidade com o Single Responsibility Principle (SRP).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InboxConsumerListener {

    private final InboxRepository inboxRepository;
    private final EventStrategyRegistry strategyRegistry;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${app.rabbitmq.queues.novo-matriculas}")
    @Transactional
    public void onMessage(Message rawMessage) {
        try {
            String messageJson = new String(rawMessage.getBody(), StandardCharsets.UTF_8);
            CloudEvent<JsonNode> event = objectMapper.readValue(
                    messageJson,
                    new TypeReference<CloudEvent<JsonNode>>() {}
            );

            log.info("[Inbox-Novo] Recebido CloudEvent: id={}, type={}, subject={}",
                    event.getId(), event.getType(), event.getSubject());

            // 1. Verificação Idempotente na Inbox
            if (inboxRepository.existsById(event.getId())) {
                log.warn("[Inbox-Novo] Mensagem duplicada detectada! ID: {}. Descartando sem reprocessar.", event.getId());
                return;
            }

            // 2. Roteamento via Padrão Strategy
            Optional<EventHandlerStrategy> strategyOpt = strategyRegistry.getStrategy(event.getType());
            if (!strategyOpt.isPresent()) {
                log.warn("[Inbox-Novo] Nenhuma estratégia registrada para o evento '{}'. Mensagem será descartada.", event.getType());
                return;
            }

            // 3. Execução da Estratégia de Negócio Dedicada
            strategyOpt.get().handle(event.getData(), event.getId());

            // 4. Registro Atômico na Inbox
            InboxMessage inbox = InboxMessage.builder()
                    .eventId(event.getId())
                    .eventType(event.getType())
                    .source(event.getSource())
                    .processedAt(Instant.now())
                    .build();
            inboxRepository.save(inbox);

            log.info("[Inbox-Novo] Evento id={} processado e registrado na Inbox com sucesso.", event.getId());

        } catch (Exception e) {
            log.error("[Inbox-Novo] Erro ao consumir mensagem: {}", e.getMessage(), e);
            throw new RuntimeException("Falha no consumo do evento", e);
        }
    }
}
