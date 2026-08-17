package com.shivang.crm.modules.workflow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkflowRabbitConfig {

    public static final String WORKFLOW_QUEUE = "crm.workflow.events";

    @Bean
    public Queue workflowEventQueue() {
        return new Queue(WORKFLOW_QUEUE, true);
    }

    @Bean
    public Binding workflowEventBinding(Queue workflowEventQueue, TopicExchange crmEventsExchange) {
        return BindingBuilder.bind(workflowEventQueue)
            .to(crmEventsExchange)
            .with("crm.#");
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