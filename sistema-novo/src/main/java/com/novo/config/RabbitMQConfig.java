package com.novo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.queues.novo-matriculas}")
    private String queueName;

    @Value("${app.rabbitmq.routing-keys.matricula-criada-v1}")
    private String routingKeyMatriculaCriada;

    @Bean
    public TopicExchange academicExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue novoMatriculasQueue() {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding bindingMatriculaCriada(Queue novoMatriculasQueue, TopicExchange academicExchange) {
        return BindingBuilder.bind(novoMatriculasQueue).to(academicExchange).with(routingKeyMatriculaCriada);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
