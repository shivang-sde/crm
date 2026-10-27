package com.shivang.crm.shared.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxEventClaimServiceTest {

    private OutboxEventRepository repository;
    private OutboxEventClaimService service;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(OutboxEventRepository.class);
        service = new OutboxEventClaimService(repository);
    }

    @Test
    void attemptBelowBudgetGoesBackToPendingWithBackoff() {
        UUID id = UUID.randomUUID();
        when(repository.markAttemptIfProcessing(eq(id), eq(OutboxEventStatus.PENDING), any(), any(), eq(null), any(), any()))
                .thenReturn(1);

        OutboxEventStatus next = service.recordFailure(id, 2, 5, "broker down", 8L);

        assertThat(next).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(next).isNotEqualTo(OutboxEventStatus.DEAD);
    }

    @Test
    void attemptAtBudgetGoesDeadAndTakesNoNextAttempt() {
        UUID id = UUID.randomUUID();
        when(repository.markAttemptIfProcessing(eq(id), eq(OutboxEventStatus.DEAD), eq(null), any(), any(), any(), any()))
                .thenReturn(1);

        OutboxEventStatus next = service.recordFailure(id, 9, 10, "broker down", 8L);

        assertThat(next).isEqualTo(OutboxEventStatus.DEAD);
        assertThat(next).isNotEqualTo(OutboxEventStatus.PENDING);
        verify(repository).markAttemptIfProcessing(eq(id), eq(OutboxEventStatus.DEAD),
                eq(null), any(), any(), eq("broker down"), any());
    }

    @Test
    void markPublishedIsGuardedOnProcessingState() {
        UUID id = UUID.randomUUID();
        when(repository.markPublishedIfProcessing(eq(id), any(), any())).thenReturn(0);

        assertThat(service.markPublished(id))
                .as("a row another worker already moved on must not be reported as published")
                .isFalse();
    }

    @Test
    void markPublishedSucceedsWhenTheRowIsStillProcessing() {
        UUID id = UUID.randomUUID();
        when(repository.markPublishedIfProcessing(eq(id), any(), any())).thenReturn(1);

        assertThat(service.markPublished(id)).isTrue();
    }

    @Test
    void requeueOnlyAppliesToDeadRows() {
        UUID id = UUID.randomUUID();
        when(repository.requeueDeadEvent(eq(id), any(), any())).thenReturn(1);

        assertThat(service.requeueDead(id)).isTrue();
        verify(repository).requeueDeadEvent(eq(id), any(), any());
    }

    @Test
    void requeueOfNonDeadRowIsANoOp() {
        UUID id = UUID.randomUUID();
        when(repository.requeueDeadEvent(eq(id), any(), any())).thenReturn(0);

        assertThat(service.requeueDead(id)).isFalse();
    }

    @Test
    void deadEventsAreCountableForAlerting() {
        when(repository.countDeadEvents()).thenReturn(7L);

        assertThat(service.countDead()).isEqualTo(7L);
    }

    @Test
    void claimUsesTheSkipLockedClaimQuery() {
        when(repository.claimAvailable(anyInt(), anyLong())).thenReturn(java.util.List.of());

        service.claim(20, 300L);

        verify(repository).claimAvailable(eq(20), eq(300L));
    }

    @Test
    void claimMarksRowsProcessingSoTheStaleReaperCannotDoubleClaim() {
        OutboxEvent row = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .status(OutboxEventStatus.PENDING)
                .attempts(0)
                .build();
        when(repository.claimAvailable(anyInt(), anyLong())).thenReturn(java.util.List.of(row));
        when(repository.saveAll(any())).thenReturn(java.util.List.of(row));

        service.claim(20, 300L);

        assertThat(row.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(row.getProcessingStartedAt()).isNotNull();
        assertThat(row.getNextAttemptAt())
                .as("a reclaimed stale row must not keep an old backoff timestamp")
                .isNull();
    }
}
