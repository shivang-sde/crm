package com.shivang.crm.shared.config;

import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shivang.crm.shared.event.ReturnedMessageTracker;

import lombok.RequiredArgsConstructor;

/**
 * Makes unroutable publishes observable.
 *
 * <p>{@code spring.rabbitmq.publisher-returns=true} only sets the AMQP
 * {@code mandatory} flag and {@code publisher-confirm-type=correlated} only tells us
 * the broker accepted the message. For a message with no matching binding the broker
 * still ACKs, so without a returns callback the publish looks successful and the
 * outbox row is marked PUBLISHED while the event is discarded.
 *
 * <p>Scope note: this covers <em>unroutable</em> publishes only. A successful
 * confirm plus no return still says nothing about whether a consumer processed the
 * message.
 */
@Configuration
@RequiredArgsConstructor
public class RabbitPublisherReturnConfig {

    @Bean
    public RabbitTemplateCustomizer rabbitTemplateReturnsCallback(ReturnedMessageTracker tracker) {
        return rabbitTemplate -> {
            rabbitTemplate.setMandatory(true);
            rabbitTemplate.setReturnsCallback(tracker::onReturned);
        };
    }
}
