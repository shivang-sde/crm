package com.shivang.crm.shared.event;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CrmEventRabbitConfig {

    public static final String EXCHANGE_NAME = "crm.events";

    @Bean
    public TopicExchange crmEventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }
}