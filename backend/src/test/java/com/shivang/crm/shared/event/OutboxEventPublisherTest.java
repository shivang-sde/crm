package com.shivang.crm.shared.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.shivang.crm.shared.resilience.InfrastructureMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.ObjectMapper;

class OutboxEventPublisherTest {

    private OutboxEventClaimService claimService;
    private ReturnedMessageTracker returnedTracker;
    private RabbitTemplate rabbitTemplate;
    private OutboxEventPublisher publisher;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        claimService = org.mockito.Mockito.mock(OutboxEventClaimService.class);
        returnedTracker = org.mockito.Mockito.mock(ReturnedMessageTracker.class);
        rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
        registry = new SimpleMeterRegistry();

        publisher = new OutboxEventPublisher(claimService, returnedTracker, rabbitTemplate,
                new ObjectMapper(), new InfrastructureMetrics(registry));
        ReflectionTestUtils.setField(publisher, "batchSize", 20);
        ReflectionTestUtils.setField(publisher, "staleAfterSeconds", 300L);
        ReflectionTestUtils.setField(publisher, "confirmTimeoutMs", 5000L);
        ReflectionTestUtils.setField(publisher, "maxRetryDelaySeconds", 300L);
        ReflectionTestUtils.setField(publisher, "maxAttempts", 3);
    }

    @Test
    void successfulPublishMarksTheRowPublished() {
        OutboxEvent event = event(0);
        when(claimService.claim(anyInt(), anyLong())).thenReturn(List.of(event));
        when(claimService.markPublished(event.getId())).thenReturn(true);

        publisher.publishAvailableEvents();

        verify(claimService, times(1)).markPublished(event.getId());
        verify(claimService, never()).recordFailure(any(), anyInt(), anyInt(), any(), anyLong());
    }

    @Test
    void brokerFailureRecordsAnAttemptAndKeepsTheRowPending() {
        OutboxEvent event = event(0);
        when(claimService.claim(anyInt(), anyLong())).thenReturn(List.of(event));
        when(rabbitTemplate.invoke(any())).thenThrow(new AmqpException("broker down"));
        when(claimService.recordFailure(any(), anyInt(), anyInt(), any(), anyLong()))
                .thenReturn(OutboxEventStatus.PENDING);

        publisher.publishAvailableEvents();

        verify(claimService).recordFailure(eq(event.getId()), eq(0), eq(3), contains("broker down"), anyLong());
        assertThat(registry.get("crm.outbox.deadlettered").counter().count()).isZero();
    }

    @Test
    void exhaustingTheAttemptBudgetMovesTheRowToDead() {
        OutboxEvent event = event(2); // this delivery is attempt 3 of 3
        when(claimService.claim(anyInt(), anyLong())).thenReturn(List.of(event));
        when(rabbitTemplate.invoke(any())).thenThrow(new AmqpException("broker down"));
        when(claimService.recordFailure(any(), anyInt(), anyInt(), any(), anyLong()))
                .thenReturn(OutboxEventStatus.DEAD);

        publisher.publishAvailableEvents();

        verify(claimService).recordFailure(eq(event.getId()), eq(2), eq(3), any(), anyLong());
        assertThat(registry.get("crm.outbox.deadlettered").counter().count())
                .as("DEAD transition must be alertable")
                .isEqualTo(1.0d);
    }

    @Test
    void unroutableMessageIsTreatedAsAFailureNotASuccess() {
        OutboxEvent event = event(0);
        when(claimService.claim(anyInt(), anyLong())).thenReturn(List.of(event));
        when(returnedTracker.wasReturned(event.getEventId().toString())).thenReturn(true);
        when(claimService.recordFailure(any(), anyInt(), anyInt(), any(), anyLong()))
                .thenReturn(OutboxEventStatus.PENDING);

        publisher.publishAvailableEvents();

        verify(claimService, never()).markPublished(any());
        verify(claimService).recordFailure(eq(event.getId()), eq(0), eq(3), contains("unroutable"), anyLong());
    }

    @Test
    void noRowsClaimedMeansNoWork() {
        when(claimService.claim(anyInt(), anyLong())).thenReturn(List.of());

        publisher.publishAvailableEvents();

        verify(rabbitTemplate, never()).invoke(any());
    }

    @Test
    void publisherStillWorksWhenTheRowIsNoLongerProcessing() {
        OutboxEvent event = event(0);
        when(claimService.claim(anyInt(), anyLong())).thenReturn(List.of(event));
        // Another worker / an operator already moved the row out of PROCESSING.
        when(claimService.markPublished(event.getId())).thenReturn(false);

        publisher.publishAvailableEvents();

        verify(claimService, never()).recordFailure(any(), anyInt(), anyInt(), any(), anyLong());
    }

    private static OutboxEvent event(int attempts) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .eventName("LEAD.CREATED")
                .aggregateType("LEAD")
                .eventType("CREATED")
                .payload(Map.of("eventId", UUID.randomUUID().toString()))
                .status(OutboxEventStatus.PROCESSING)
                .attempts(attempts)
                .build();
    }
}
