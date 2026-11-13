package com.novo.service;

import com.novo.domain.OutboxMessage;
import com.novo.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherWorker {

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Scheduled(fixedDelayString = "${app.outbox.interval-ms:2000}")
    @Transactional
    public void processOutbox() {
        List<OutboxMessage> pending = outboxRepository.findPendingMessages(PageRequest.of(0, 10));
        if (pending.isEmpty()) {
            return;
        }

        for (OutboxMessage msg : pending) {
            try {
                MessageProperties properties = new MessageProperties();
                properties.setContentType("application/json");
                properties.setContentEncoding("UTF-8");
                Message rabbitMsg = new Message(msg.getPayloadJson().getBytes(StandardCharsets.UTF_8), properties);

                rabbitTemplate.send(exchangeName, msg.getRoutingKey(), rabbitMsg);

                msg.setProcessed(true);
                msg.setProcessedAt(Instant.now());
                outboxRepository.save(msg);

                log.info("[OutboxWorker-Novo] Evento publicado no RabbitMQ: id={}, routingKey={}", msg.getId(), msg.getRoutingKey());
            } catch (Exception e) {
                log.error("[OutboxWorker-Novo] Falha ao publicar mensagem id={}: {}", msg.getId(), e.getMessage());
            }
        }
    }
}
