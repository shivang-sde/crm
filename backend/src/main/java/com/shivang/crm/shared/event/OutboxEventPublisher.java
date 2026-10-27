package com.shivang.crm.shared.event;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.databind.ObjectMapper;

import com.shivang.crm.shared.resilience.InfrastructureMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Drains the transactional outbox to RabbitMQ.
 *
 * <p>Lifecycle after this change:
 * <pre>
 *   PENDING -> PROCESSING -> PUBLISHED
 *                       \-> PENDING (backoff) ... until attempts >= maxAttempts
 *                       \-> DEAD      (terminal, operator requeue only)
 * </pre>
 *
 * <p>Two independent delivery-failure modes are handled:
 * <ul>
 *   <li><b>Broker/connection failure</b> - the send or the confirm throws.</li>
 *   <li><b>Unroutable message</b> - the broker accepts and <em>acknowledges</em> the
 *       publish even though no queue is bound for the routing key. A confirm alone
 *       does not prove routability, so {@link ReturnedMessageTracker} is consulted
 *       after the confirm and the row is treated as a failure. Without this an
 *       unroutable event is marked PUBLISHED and lost with no trace.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    /** Raised internally when the broker returned the message as unroutable. */
    static final class UnroutableMessageException extends RuntimeException {
        UnroutableMessageException(String routingKey) {
            super("Broker returned the message as unroutable for routing key " + routingKey);
        }
    }

    private final OutboxEventClaimService outboxEventClaimService;
    private final ReturnedMessageTracker returnedMessageTracker;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final InfrastructureMetrics metrics;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Value("${app.outbox.stale-after-seconds:300}")
    private long staleAfterSeconds;

    @Value("${app.outbox.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs;

    @Value("${app.outbox.max-retry-delay-seconds:300}")
    private long maxRetryDelaySeconds;

    /**
     * Publish attempt budget per row. Conservative default: with the existing backoff
     * (2s, 4s, 8s ... capped at max-retry-delay-seconds) this spans roughly 20 minutes
     * of sustained broker unavailability before a row is parked in DEAD.
     */
    @Value("${app.outbox.max-attempts:10}")
    private int maxAttempts = 10;

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:5000}", initialDelayString = "${app.outbox.poll-delay-ms:5000}")
    public void publishAvailableEvents() {
        List<OutboxEvent> events = outboxEventClaimService.claim(batchSize, staleAfterSeconds);
        events.forEach(this::publishOne);
    }

    private void publishOne(OutboxEvent event) {
        String routingKey = "crm." + event.getAggregateType() + "." + event.getEventType();
        try {
            Message message = MessageBuilder
                .withBody(objectMapper.writeValueAsString(event.getPayload()).getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setMessageId(event.getEventId().toString())
                .build();

            rabbitTemplate.invoke(operations -> {
                operations.send(CrmEventRabbitConfig.EXCHANGE_NAME, routingKey, message);
                operations.waitForConfirmsOrDie(confirmTimeoutMs);
                return null;
            });

            // A broker confirm only proves the broker took responsibility for the
            // message. For a mandatory publish of an unroutable message the broker
            // still ACKs, and basic.return is emitted before the confirming ack, so
            // the return is already recorded at this point.
            if (returnedMessageTracker.wasReturned(event.getEventId().toString())) {
                throw new UnroutableMessageException(routingKey);
            }

            if (!outboxEventClaimService.markPublished(event.getId())) {
                log.warn("CRM outbox event was not in PROCESSING state at completion; skipping status update. eventId={}",
                        event.getEventId());
            }
        } catch (Exception ex) {
            OutboxEventStatus next = outboxEventClaimService.recordFailure(
                    event.getId(), event.getAttempts() == null ? 0 : event.getAttempts(),
                    maxAttempts, messageFor(ex), retryDelaySeconds(event.getAttempts() == null ? 1 : event.getAttempts() + 1));

            if (next == OutboxEventStatus.DEAD) {
                metrics.recordOutboxDeadLetter(event.getEventName());
            }
        }
    }

    private long retryDelaySeconds(int attempts) {
        long delay = 1L << Math.min(attempts, 8);
        return Math.min(delay, maxRetryDelaySeconds);
    }

    private String messageFor(Exception exception) {
        if (exception instanceof JsonProcessingException) {
            return "Unable to serialize canonical CRM event: " + exception.getMessage();
        }
        if (exception instanceof UnroutableMessageException) {
            return exception.getMessage();
        }
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
