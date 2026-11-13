package br.com.legado.consumer.strategy;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Interface Strategy para tratamento desacoplado de eventos recebidos pela Inbox no Sistema Legado.
 */
public interface EventHandlerStrategy {

    /**
     * Retorna o tipo de evento suportado (ex: matricula.status.atualizado.v1).
     */
    String getSupportedEventType();

    /**
     * Processa a regra de negócio específica do evento recebido.
     *
     * @param eventData Dados brutos em JsonNode
     * @param eventId   Identificador único do CloudEvent
     */
    void handle(JsonNode eventData, String eventId);
}
