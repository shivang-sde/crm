package com.shivang.crm.modules.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import com.rabbitmq.client.Channel;
import com.shivang.crm.modules.workflow.config.WorkflowRabbitConfig;
import com.shivang.crm.shared.event.CanonicalCrmEvent;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.resilience.InfrastructureMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.ObjectMapper;

class WorkflowEventConsumerTest {

    private final WorkflowTriggerService triggerService = mock(WorkflowTriggerService.class);
    private final Channel channel = mock(Channel.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private WorkflowEventConsumer consumer;
    private ObjectMapper objectMapper;
    private CanonicalCrmEvent event;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new WorkflowEventConsumer(objectMapper, triggerService, new InfrastructureMetrics(registry));
        event = CanonicalCrmEvent.forEntity("LEAD", "CREATED", UUID.randomUUID(), UUID.randomUUID(),
                java.util.Map.of("actorId", UUID.randomUUID().toString()));
    }

    @Test
    void successAcknowledgesTheDelivery() throws Exception {
        consumer.consume(messageFor(event), channel);

        verify(triggerService, times(1)).process(event);
        verify(channel, times(1)).basicAck(anyLong(), eq(false));
    }

    @Test
    void malformedPayloadIsRejectedWithoutRetrying() throws Exception {
        Message malformed = MessageBuilder.withBody("not json".getBytes())
                .build();

        consumer.consume(malformed, channel);

        verify(channel, times(1)).basicReject(anyLong(), eq(false));
        verify(triggerService, never()).process(any());
    }

    @Test
    void incompleteIdentityIsRejectedWithoutRetrying() throws Exception {
        CanonicalCrmEvent incomplete = new CanonicalCrmEvent(
                event.eventId(), "LEAD", "CREATED", event.tenantId(), null, event.occurredAt(),
                java.util.Map.of());

        consumer.consume(messageFor(incomplete), channel);

        verify(channel, times(1)).basicReject(anyLong(), eq(false));
        verify(triggerService, never()).process(any());
    }

    @Test
    void deterministicBusinessFailureBypassesRetryAndDeadLetters() {
        doThrow(new BusinessException("WORKFLOW_TRIGGER_FILTER_INVALID", "bad filter"))
                .when(triggerService).process(any());

        assertThatThrownBy(() -> consumer.consume(messageFor(event), channel))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(registry.get("crm.rabbitmq.delivery.retried").counter().count())
                .as("deterministic failures must not consume the retry budget")
                .isZero();
    }

    @Test
    void deterministicWorkflowFailureBypassesRetry() {
        doThrow(new WorkflowRuntimeException("WORKFLOW_ACTOR_INVALID", "bad actor"))
                .when(triggerService).process(any());

        assertThatThrownBy(() -> consumer.consume(messageFor(event), channel))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void transientFailurePropagatesToTheRetryAdviceAndIsCounted() {
        doThrow(new CannotAcquireLockException("row lock unavailable"))
                .when(triggerService).process(any());

        assertThatThrownBy(() -> consumer.consume(messageFor(event), channel))
                .isNotInstanceOf(AmqpRejectAndDontRequeueException.class)
                .isInstanceOf(CannotAcquireLockException.class);

        assertThat(registry.get("crm.rabbitmq.delivery.retried").counter().count())
                .as("a transient failure must be visible even if a later delivery succeeds")
                .isEqualTo(1.0d);
    }

    @Test
    void integrityViolationIsTreatedAsDeterministic() {
        doThrow(new DataIntegrityViolationException("unique violation"))
                .when(triggerService).process(any());

        assertThatThrownBy(() -> consumer.consume(messageFor(event), channel))
                .isNotInstanceOf(AmqpRejectAndDontRequeueException.class)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listenerIsNotAcknowledgedOnFailure() throws Exception {
        doThrow(new CannotAcquireLockException("down")).when(triggerService).process(any());

        assertThatThrownBy(() -> consumer.consume(messageFor(event), channel))
                .isInstanceOf(CannotAcquireLockException.class);

        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    @Test
    void queueNameMatchesTheConfiguredTopology() {
        assertThat(WorkflowRabbitConfig.WORKFLOW_QUEUE).isEqualTo("crm.workflow.events");
        assertThat(WorkflowRabbitConfig.WORKFLOW_DEAD_LETTER_QUEUE).isEqualTo("crm.workflow.events.dlq");
    }

    private Message messageFor(CanonicalCrmEvent value) {
        try {
            return MessageBuilder.withBody(objectMapper.writeValueAsString(value).getBytes()).build();
        } catch (Exception ex) {
            throw new IllegalStateException("test payload could not be serialized", ex);
        }
    }
}
