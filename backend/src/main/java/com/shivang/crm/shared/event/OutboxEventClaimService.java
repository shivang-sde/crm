package com.shivang.crm.shared.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns every state transition of a claimed outbox row.
 *
 * <p>Transitions live here rather than in the publisher because they need a real
 * transaction boundary. The previous implementation called {@code @Transactional}
 * methods on itself, which bypasses the Spring proxy and left the rows detached —
 * meaning the publish and the status update were never actually atomic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventClaimService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public List<OutboxEvent> claim(int batchSize, long staleAfterSeconds) {
        List<OutboxEvent> events = outboxEventRepository.claimAvailable(batchSize, staleAfterSeconds);
        Instant now = Instant.now();
        events.forEach(event -> {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setProcessingStartedAt(now);
            event.setNextAttemptAt(null);
        });
        return outboxEventRepository.saveAll(events);
    }

    /**
     * Marks a claimed row as published. Returns false when the row was no longer in
     * PROCESSING (for example an operator already requeued a DEAD row), in which case
     * the caller must not treat the delivery as the source of truth.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPublished(UUID id) {
        Instant now = Instant.now();
        return outboxEventRepository.markPublishedIfProcessing(id, now, now) > 0;
    }

    /**
     * Records a failed attempt and chooses the next state.
     *
     * <p>When {@code maxAttempts} is reached the row is moved to the terminal
     * {@link OutboxEventStatus#DEAD} state and is never selected for publishing
     * again. Nothing re-queues it automatically.
     *
     * @return the state the row was moved to
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OutboxEventStatus recordFailure(UUID id, int attemptsSoFar, int maxAttempts, String error,
            long retryDelaySeconds) {
        Instant now = Instant.now();
        int attempts = attemptsSoFar + 1;
        boolean exhausted = attempts >= maxAttempts;
        OutboxEventStatus status = exhausted ? OutboxEventStatus.DEAD : OutboxEventStatus.PENDING;
        Instant nextAttemptAt = now.plusSeconds(Math.max(0L, retryDelaySeconds));

        outboxEventRepository.markAttemptIfProcessing(
                id,
                status,
                exhausted ? null : nextAttemptAt,
                exhausted ? now : nextAttemptAt,
                exhausted ? now : null,
                error,
                now);

        if (exhausted) {
            log.error("CRM outbox event moved to terminal DEAD state after {} attempts. eventId={}. "
                            + "It will not be republished automatically; requeue it explicitly once the cause is fixed. "
                            + "lastError={}", attempts, id, error);
        } else {
            log.warn("CRM outbox event publish failed; retry {}/{} scheduled at {}. eventId={}. lastError={}",
                    attempts, maxAttempts, nextAttemptAt, id, error);
        }
        return status;
    }

    /** Explicit operator-only recovery for a DEAD row. Never called by a scheduler. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean requeueDead(UUID id) {
        Instant now = Instant.now();
        boolean requeued = outboxEventRepository.requeueDeadEvent(id, now, now) > 0;
        if (requeued) {
            log.warn("CRM outbox event explicitly requeued by operator. eventId={}", id);
        }
        return requeued;
    }

    @Transactional(readOnly = true)
    public long countDead() {
        return outboxEventRepository.countDeadEvents();
    }
}
