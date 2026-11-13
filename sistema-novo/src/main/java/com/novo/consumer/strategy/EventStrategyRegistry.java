package com.novo.consumer.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry responsável por gerenciar e localizar a Strategy adequada com base no 'type' do evento.
 * Utiliza injeção automática de dependências do Spring (Open/Closed Principle).
 */
@Component
@Slf4j
public class EventStrategyRegistry {

    private final Map<String, EventHandlerStrategy> strategies;

    public EventStrategyRegistry(List<EventHandlerStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        EventHandlerStrategy::getSupportedEventType,
                        Function.identity(),
                        (existing, duplicate) -> {
                            log.warn("Estratégia duplicada encontrada para evento: {}. Mantendo a primeira.", existing.getSupportedEventType());
                            return existing;
                        }
                ));
        log.info("[EventStrategyRegistry-Novo] {} estratégias registradas: {}", strategies.size(), strategies.keySet());
    }

    public Optional<EventHandlerStrategy> getStrategy(String eventType) {
        return Optional.ofNullable(strategies.get(eventType));
    }
}
