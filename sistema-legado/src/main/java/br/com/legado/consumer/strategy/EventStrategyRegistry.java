package br.com.legado.consumer.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry responsável por gerenciar e localizar a Strategy adequada no Sistema Legado (Java 8).
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
        log.info("[EventStrategyRegistry-Legado] {} estratégias registradas: {}", strategies.size(), strategies.keySet());
    }

    public Optional<EventHandlerStrategy> getStrategy(String eventType) {
        return Optional.ofNullable(strategies.get(eventType));
    }
}
