package com.novo.consumer.strategy;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Interface Strategy para tratamento desacoplado de eventos recebidos pela Inbox.
 * Cada implementação lida exclusivamente com a regra de negócio de um tipo específico de evento.
 */
public interface EventHandlerStrategy {

    /**
     * Retorna a chave do tipo de evento suportado (ex: aluno.matricula.criada.v1).
     */
    String getSupportedEventType();

    /**
     * Executa a regra de negócio do evento.
     *
     * @param eventData Dados brutos do evento (JsonNode)
     * @param eventId   Identificador único do CloudEvent (para correlação e logs)
     */
    void handle(JsonNode eventData, String eventId);
}
