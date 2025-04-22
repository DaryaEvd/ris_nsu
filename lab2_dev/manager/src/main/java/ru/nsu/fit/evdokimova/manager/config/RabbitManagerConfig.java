package ru.nsu.fit.evdokimova.manager.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitManagerConfig {
    public static final String CRACK_HASH_EXCHANGE = "crack.hash.exchange";

    public static final String TASKS_QUEUE = "tasks.queue";
    public static final String TASKS_ROUTING_KEY = "task";

    public static final String RESULTS_QUEUE = "results.queue";
    public static final String RESULTS_ROUTING_KEY = "result";

    @Bean
    public DirectExchange crackHashExchange() {
        return new DirectExchange(CRACK_HASH_EXCHANGE, true, false);
    }

    @Bean
    public Queue tasksQueue() {
        return new Queue(TASKS_QUEUE, true);
    }

    @Bean
    public Queue resultsQueue() {
        return new Queue(RESULTS_QUEUE, true);
    }

    @Bean
    public Binding tasksBinding() {
        return BindingBuilder.bind(tasksQueue())
                .to(crackHashExchange())
                .with(TASKS_ROUTING_KEY);
    }

    @Bean
    public Binding resultsBinding() {
        return BindingBuilder.bind(resultsQueue())
                .to(crackHashExchange())
                .with(RESULTS_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        template.setMandatory(true);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}