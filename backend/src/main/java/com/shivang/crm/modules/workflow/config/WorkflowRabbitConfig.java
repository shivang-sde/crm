package com.shivang.crm.modules.workflow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkflowRabbitConfig {

    public static final String WORKFLOW_QUEUE = "crm.workflow.events";

    // Poison messages rejected without requeue by WorkflowEventConsumer land here instead of being lost.
    public static final String WORKFLOW_DEAD_LETTER_EXCHANGE = "crm.workflow.events.dlx";
    public static final String WORKFLOW_DEAD_LETTER_QUEUE = "crm.workflow.events.dlq";
    public static final String WORKFLOW_DEAD_LETTER_ROUTING_KEY = "crm.workflow.events";

    @Bean
    public Queue workflowEventQueue() {
        return QueueBuilder.durable(WORKFLOW_QUEUE)
            .withArgument("x-dead-letter-exchange", WORKFLOW_DEAD_LETTER_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", WORKFLOW_DEAD_LETTER_ROUTING_KEY)
            .build();
    }

    @Bean
    public Binding workflowEventBinding(Queue workflowEventQueue, TopicExchange crmEventsExchange) {
        return BindingBuilder.bind(workflowEventQueue)
            .to(crmEventsExchange)
            .with("crm.#");
    }

    @Bean
    public DirectExchange workflowDeadLetterExchange() {
        return new DirectExchange(WORKFLOW_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue workflowDeadLetterQueue() {
        return QueueBuilder.durable(WORKFLOW_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding workflowDeadLetterBinding(Queue workflowDeadLetterQueue, DirectExchange workflowDeadLetterExchange) {
        return BindingBuilder.bind(workflowDeadLetterQueue)
            .to(workflowDeadLetterExchange)
            .with(WORKFLOW_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory workflowRabbitListenerContainerFactory(
        CachingConnectionFactory connectionFactory
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        return factory;
    }
}