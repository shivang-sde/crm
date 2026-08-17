package com.shivang.crm.shared.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
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
}