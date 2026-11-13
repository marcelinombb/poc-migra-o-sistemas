package br.com.legado.config;

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

    @Value("${app.rabbitmq.queues.legado-matriculas}")
    private String queueName;

    @Value("${app.rabbitmq.routing-keys.matricula-status-atualizado-v1}")
    private String routingKeyStatusAtualizado;

    @Bean
    public TopicExchange academicExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue legadoMatriculasQueue() {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding bindingStatusAtualizado(Queue legadoMatriculasQueue, TopicExchange academicExchange) {
        return BindingBuilder.bind(legadoMatriculasQueue).to(academicExchange).with(routingKeyStatusAtualizado);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
