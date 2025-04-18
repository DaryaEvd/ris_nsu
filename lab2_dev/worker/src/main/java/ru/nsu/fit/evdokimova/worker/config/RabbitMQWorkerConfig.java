package ru.nsu.fit.evdokimova.worker.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQWorkerConfig {
    public static final String TASKS_EXCHANGE = "tasks.exchange";
    public static final String RESULTS_QUEUE = "results.queue";
    public static final String RESULTS_ROUTING_KEY = "results.key";

    @Bean
    public DirectExchange tasksExchange() {
        return new DirectExchange(TASKS_EXCHANGE);
    }

    @Bean
    public Queue workerQueue(@Value("${worker.port}") String workerPort) {
        return new Queue("worker.queue." + workerPort, true);
    }

    @Bean
    public Binding workerBinding(Queue workerQueue, DirectExchange tasksExchange,
                                 @Value("${worker.port}") String workerPort) {
        return BindingBuilder.bind(workerQueue)
                .to(tasksExchange)
                .with("worker." + workerPort.substring(workerPort.length() - 1));
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }
}
