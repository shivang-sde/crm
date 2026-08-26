package com.shivang.crm.shared.event;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventClaimService outboxEventClaimService;
    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Value("${app.outbox.stale-after-seconds:300}")
    private long staleAfterSeconds;

    @Value("${app.outbox.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs;

    @Value("${app.outbox.max-retry-delay-seconds:300}")
    private long maxRetryDelaySeconds;

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:5000}", initialDelayString = "${app.outbox.poll-delay-ms:5000}")
    public void publishAvailableEvents() {
        List<OutboxEvent> events = outboxEventClaimService.claim(batchSize, staleAfterSeconds);
        events.forEach(this::publishOne);
    }

    private void publishOne(OutboxEvent event) {
        try {
            Message message = MessageBuilder
                .withBody(objectMapper.writeValueAsString(event.getPayload()).getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setMessageId(event.getEventId().toString())
                .build();

            rabbitTemplate.invoke(operations -> {
                operations.send(
                    CrmEventRabbitConfig.EXCHANGE_NAME,
                    "crm." + event.getAggregateType() + "." + event.getEventType(),
                    message
                );
                operations.waitForConfirmsOrDie(confirmTimeoutMs);
                return null;
            });

            markPublished(event);
        } catch (Exception ex) {
            markFailed(event, ex);
        }
    }

    @Transactional
    protected void markPublished(OutboxEvent event) {
        event.setStatus(OutboxEventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        event.setProcessingStartedAt(null);
        event.setLastError(null);
        outboxEventRepository.save(event);
    }

    @Transactional
    protected void markFailed(OutboxEvent event, Exception exception) {
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setStatus(OutboxEventStatus.PENDING);
        event.setProcessingStartedAt(null);
        event.setLastError(messageFor(exception));
        event.setNextAttemptAt(Instant.now().plusSeconds(retryDelaySeconds(attempts)));
        event.setAvailableAt(event.getNextAttemptAt());
        outboxEventRepository.save(event);
        log.warn("CRM outbox event {} publish failed; retry {} scheduled at {}", event.getEventId(), attempts, event.getNextAttemptAt());
    }

    private long retryDelaySeconds(int attempts) {
        long delay = 1L << Math.min(attempts, 8);
        return Math.min(delay, maxRetryDelaySeconds);
    }

    private String messageFor(Exception exception) {
        if (exception instanceof JsonProcessingException) {
            return "Unable to serialize canonical CRM event: " + exception.getMessage();
        }
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}