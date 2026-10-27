package com.shivang.crm.shared.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
        SELECT *
        FROM crm_event_outbox
        WHERE deleted = false
          AND (
              (status = 'PENDING' AND available_at <= now())
              OR (status = 'PROCESSING' AND processing_started_at <= now() - CAST(:staleAfterSeconds || ' seconds' AS interval))
          )
        ORDER BY available_at ASC
        FOR UPDATE SKIP LOCKED
        LIMIT :batchSize
        """, nativeQuery = true)
    List<OutboxEvent> claimAvailable(
        @Param("batchSize") int batchSize,
        @Param("staleAfterSeconds") long staleAfterSeconds
    );

    /**
     * Terminal transition to PUBLISHED.
     *
     * <p>Guarded by {@code status = 'PROCESSING'} so a row that another worker already
     * moved to DEAD (or that was requeued by an operator) can never be resurrected by
     * a late publisher. Returns the number of rows updated; 0 means the transition was
     * a no-op and the caller must not assume the event was published.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE OutboxEvent o
           SET o.status = com.shivang.crm.shared.event.OutboxEventStatus.PUBLISHED,
               o.publishedAt = :publishedAt,
               o.processingStartedAt = null,
               o.lastError = null,
               o.updatedAt = :updatedAt
         WHERE o.id = :id
           AND o.status = com.shivang.crm.shared.event.OutboxEventStatus.PROCESSING
           AND o.deleted = false
        """)
    int markPublishedIfProcessing(
        @Param("id") UUID id,
        @Param("publishedAt") Instant publishedAt,
        @Param("updatedAt") Instant updatedAt
    );

    /**
     * Records a failed attempt and moves the row to the supplied terminal/intermediate
     * state. {@code attempts} is incremented in the same statement, so concurrent
     * workers cannot lose an increment.
     *
     * <p>{@code nextAttemptAt} is only meaningful for PENDING; DEAD rows get
     * {@code deadAt} instead. The {@code status = 'PROCESSING'} guard is what makes
     * DEAD terminal.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE OutboxEvent o
           SET o.attempts = COALESCE(o.attempts, 0) + 1,
               o.status = :status,
               o.processingStartedAt = null,
               o.nextAttemptAt = :nextAttemptAt,
               o.availableAt = :availableAt,
               o.deadAt = :deadAt,
               o.lastError = :lastError,
               o.updatedAt = :updatedAt
         WHERE o.id = :id
           AND o.status = com.shivang.crm.shared.event.OutboxEventStatus.PROCESSING
           AND o.deleted = false
        """)
    int markAttemptIfProcessing(
        @Param("id") UUID id,
        @Param("status") OutboxEventStatus status,
        @Param("nextAttemptAt") Instant nextAttemptAt,
        @Param("availableAt") Instant availableAt,
        @Param("deadAt") Instant deadAt,
        @Param("lastError") String lastError,
        @Param("updatedAt") Instant updatedAt
    );

    /**
     * Explicit operator recovery. Deliberately not exposed on any timer or listener:
     * a DEAD event is only requeued on human request.
     *
     * <p>{@code attempts}, {@code lastError} and {@code deadAt} are intentionally left
     * untouched. The operator needs to still see that the event failed before, and how
     * it failed, while it is retrying - the row carries its failure history through the
     * new attempt cycle. {@code deadAt} on a PENDING row is only read for display and
     * ordering, so this costs no correctness. The pre-existing failure path in
     * {@code markAttemptIfProcessing} still owns rewriting both columns when the
     * requeued event's new attempt resolves.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE OutboxEvent o
           SET o.status = com.shivang.crm.shared.event.OutboxEventStatus.PENDING,
               o.availableAt = :availableAt,
               o.nextAttemptAt = :availableAt,
               o.updatedAt = :updatedAt
         WHERE o.id = :id
           AND o.status = com.shivang.crm.shared.event.OutboxEventStatus.DEAD
           AND o.deleted = false
        """)
    int requeueDeadEvent(
        @Param("id") UUID id,
        @Param("availableAt") Instant availableAt,
        @Param("updatedAt") Instant updatedAt
    );

    @Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.status = com.shivang.crm.shared.event.OutboxEventStatus.DEAD AND o.deleted = false")
    long countDeadEvents();

    /**
     * Terminal (DEAD) rows for one tenant, newest first.
     *
     * <p>Explicit tenant predicate rather than a nullable-parameter trick, so it is
     * impossible for a platform-scoped query to accidentally widen.
     */
    @Query("""
        SELECT o FROM OutboxEvent o
         WHERE o.status = com.shivang.crm.shared.event.OutboxEventStatus.DEAD
           AND o.deleted = false
           AND o.tenantId = :tenantId
         ORDER BY o.deadAt DESC NULLS LAST, o.createdAt DESC
        """)
    Page<OutboxEvent> findDeadForTenant(@Param("tenantId") UUID tenantId, Pageable pageable);

    /** Terminal (DEAD) rows across every tenant. Platform-operator scope only. */
    @Query("""
        SELECT o FROM OutboxEvent o
         WHERE o.status = com.shivang.crm.shared.event.OutboxEventStatus.DEAD
           AND o.deleted = false
         ORDER BY o.deadAt DESC NULLS LAST, o.createdAt DESC
        """)
    Page<OutboxEvent> findDeadPlatformWide(Pageable pageable);

    /**
     * Loads a DEAD row by id.
     *
     * <p>Used to read the persisted tenant for the authorization check. The requeue
     * itself is still the conditional update in {@code OutboxEventClaimService}, so
     * a stale read cannot turn into a privilege escalation: the update is not
     * tenant-guarded in SQL, it is guarded by {@code status = 'DEAD'}, and the
     * authorization decision is made from this load immediately before it.
     */
    Optional<OutboxEvent> findByIdAndStatusAndDeletedFalse(UUID id, OutboxEventStatus status);
}
