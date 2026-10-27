package com.shivang.crm.shared.event;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.stereotype.Component;

import com.shivang.crm.shared.resilience.InfrastructureMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Records messages the broker accepted but could not route to any queue.
 *
 * <p>RabbitMQ acknowledges a {@code mandatory} publish even when it is
 * unroutable, so a publisher confirm alone is <em>not</em> proof of delivery.
 * Without this tracker a missing binding silently discards CRM events: the
 * publisher confirms, the outbox row is marked PUBLISHED, and nothing ever
 * consumes the event.
 *
 * <p>Scope: this only covers <em>unroutable</em> publishes. It says nothing
 * about whether a consumer processed the message.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReturnedMessageTracker {

    /** Bounded so a long broker misconfiguration cannot grow memory without limit. */
    private static final int MAX_TRACKED = 10_000;
    private static final Duration RETENTION = Duration.ofMinutes(5);

    private final Map<String, Long> returned = new ConcurrentHashMap<>();

    private final InfrastructureMetrics metrics;

    public void onReturned(ReturnedMessage message) {
        String messageId = message.getMessage() == null || message.getMessage().getMessageProperties() == null
                ? null
                : message.getMessage().getMessageProperties().getMessageId();
        prune();

        if (messageId != null) {
            returned.put(messageId, System.nanoTime());
        }

        metrics.recordRabbitReturned(message.getExchange(), message.getReplyCode());

        // Structured metadata only: exchange, routing key, reply code/text and the
        // message id. The body is deliberately never logged - it carries tenant data.
        log.error("RabbitMQ publisher return: message was accepted by the broker but could not be routed. "
                        + "exchange={}, routingKey={}, replyCode={}, replyText={}, messageId={}. "
                        + "No consumer will see this message - check the binding for the routing key.",
                message.getExchange(), message.getRoutingKey(), message.getReplyCode(),
                message.getReplyText(), messageId);
    }

    /**
     * Consumes the record for {@code messageId}, returning true if the broker
     * returned the message as unroutable.
     *
     * <p>Safe to call right after a confirm: the AMQP protocol emits
     * {@code basic.return} before the {@code basic.ack} that completes the
     * publisher confirm, so by the time the confirm is observed the return has
     * already been delivered to the callback.
     */
    public boolean wasReturned(String messageId) {
        return messageId != null && returned.remove(messageId) != null;
    }

    private void prune() {
        if (returned.size() < MAX_TRACKED) {
            return;
        }
        long cutoff = System.nanoTime() - RETENTION.toNanos();
        returned.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        if (returned.size() >= MAX_TRACKED) {
            returned.clear();
        }
    }
}
