package com.shivang.crm.modules.recurrence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.recurrence.entity.RecurrenceSchedule;
import com.shivang.crm.modules.reminder.entity.ReminderSourceType;

@Repository
public interface RecurrenceScheduleRepository extends JpaRepository<RecurrenceSchedule, UUID> {

    Optional<RecurrenceSchedule> findByTenantIdAndSourceTypeAndSourceIdAndDeletedFalse(
            UUID tenantId,
            ReminderSourceType sourceType,
            UUID sourceId
    );

    @Query(value = """
            SELECT *
            FROM recurrence_schedules
            WHERE active = true
              AND deleted = false
              AND next_occurrence_at IS NOT NULL
              AND next_occurrence_at <= :windowEnd
            ORDER BY next_occurrence_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<RecurrenceSchedule> findDueSchedulesForGeneration(
            @Param("windowEnd") Instant windowEnd,
            @Param("batchSize") int batchSize
    );
}
