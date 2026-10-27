package com.shivang.crm.shared.resilience;

import java.util.function.BooleanSupplier;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Central registry for infrastructure alert signals.
 *
 * <p>Counter names are stable: dashboards and alerts depend on them, so rename
 * rather than silently repurpose.
 */
@Component
public class InfrastructureMetrics {

    private final MeterRegistry meterRegistry;

    private final Counter redisDegraded;
    private final Counter circuitOpened;
    private final Counter circuitClosed;
    private final Counter rabbitRetried;
    private final Counter rabbitDeadLettered;
    private final Counter rabbitReturned;
    private final Counter outboxDeadLettered;
    private final Counter loginThrottled;
    private final Counter loginThrottleFallback;
    private final Counter operatorActions;

    public InfrastructureMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.redisDegraded = counter("crm.redis.degraded",
                "Redis-backed operations degraded because Redis was unreachable");
        this.circuitOpened = counter("crm.resilience.circuit.opened", "Dependency bypass windows opened");
        this.circuitClosed = counter("crm.resilience.circuit.closed",
                "Dependency bypass windows closed after a successful probe");
        this.rabbitRetried = counter("crm.rabbitmq.delivery.retried",
                "RabbitMQ deliveries that failed and were handed to the retry cycle");
        this.rabbitDeadLettered = counter("crm.rabbitmq.delivery.deadlettered",
                "RabbitMQ deliveries rejected to the dead-letter queue");
        this.rabbitReturned = counter("crm.rabbitmq.publisher.returned",
                "Messages accepted by the broker but unroutable (publisher return)");
        this.outboxDeadLettered = counter("crm.outbox.deadlettered",
                "Outbox rows moved to the terminal DEAD state after exhausting publish attempts");
        this.loginThrottled = counter("crm.auth.login.throttled",
                "Login attempts refused because the brute-force window was exceeded");
        this.loginThrottleFallback = counter("crm.auth.login.throttle.fallback",
                "Login throttles served by the in-process fallback because Redis was unreachable");
        this.operatorActions = counter("crm.operator.actions",
                "Explicit operator recovery actions attempted");
    }

    private Counter counter(String name, String description) {
        return Counter.builder(name).description(description).register(meterRegistry);
    }

    private void countBy(String name, String... tags) {
        Counter.Builder builder = Counter.builder(name);
        for (int i = 0; i + 1 < tags.length; i += 2) {
            builder.tag(tags[i], tags[i + 1]);
        }
        builder.register(meterRegistry).increment();
    }

    /** Redis-backed operations skipped or degraded because Redis is unavailable. */
    public void recordRedisDegraded(String operation, Throwable cause) {
        redisDegraded.increment();
        countBy("crm.redis.degraded.by",
                "operation", operation,
                "reason", cause == null ? "unknown" : cause.getClass().getSimpleName());
    }

    public void recordCircuitOpened(String dependency) {
        circuitOpened.increment();
        countBy("crm.resilience.circuit.opened.by", "dependency", dependency);
    }

    public void recordCircuitClosed(String dependency) {
        circuitClosed.increment();
        countBy("crm.resilience.circuit.closed.by", "dependency", dependency);
    }

    public void recordRabbitRetry(String queue) {
        rabbitRetried.increment();
        countBy("crm.rabbitmq.delivery.retried.by", "queue", queue);
    }

    public void recordRabbitDeadLettered(String queue) {
        rabbitDeadLettered.increment();
        countBy("crm.rabbitmq.delivery.deadlettered.by", "queue", queue);
    }

    /**
     * A message was accepted by the broker but could not be routed to any queue.
     * This does NOT mean the message was delivered to a consumer.
     */
    public void recordRabbitReturned(String exchange, int replyCode) {
        rabbitReturned.increment();
        countBy("crm.rabbitmq.publisher.returned.by",
                "exchange", exchange == null ? "unknown" : exchange,
                "replyCode", String.valueOf(replyCode));
    }

    public void recordOutboxDeadLetter(String eventName) {
        outboxDeadLettered.increment();
        countBy("crm.outbox.deadlettered.by", "event", eventName == null ? "unknown" : eventName);
    }

    public void recordLoginThrottled() {
        loginThrottled.increment();
    }

    public void recordLoginThrottleBackendFallback() {
        loginThrottleFallback.increment();
    }

    /** One explicit operator recovery attempt. Recorded for SUCCESS and failure alike. */
    public void recordOperatorAction(String actionType, String result) {
        operatorActions.increment();
        countBy("crm.operator.actions.by", "action", actionType, "result", result);
    }

    /**
     * Exposes dependency reachability as a 0/1 gauge so an outage is alertable
     * even when no request has been served yet.
     */
    public void registerRedisAvailabilityGauge(String dependency, BooleanSupplier supplier) {
        Gauge.builder("crm.redis.available", supplier, s -> s.getAsBoolean() ? 1d : 0d)
                .tag("dependency", dependency)
                .description("1 when Redis answered the last probe, 0 when it is being bypassed")
                .register(meterRegistry);
    }
}
