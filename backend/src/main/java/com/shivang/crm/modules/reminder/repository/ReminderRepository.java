package com.shivang.crm.modules.reminder.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.reminder.entity.Reminder;
import com.shivang.crm.modules.reminder.entity.ReminderSourceType;
import com.shivang.crm.modules.reminder.entity.ReminderStatus;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    List<Reminder> findByTenantIdAndSourceTypeAndSourceId(UUID tenantId, ReminderSourceType sourceType, UUID sourceId);

    boolean existsByTenantIdAndSourceTypeAndSourceIdAndOccurrenceAtAndScheduledAt(
        UUID tenantId,
        ReminderSourceType sourceType,
        UUID sourceId,
        java.time.Instant occurrenceAt,
        java.time.Instant scheduledAt
    );

    @Modifying
    @Transactional
    @Query("UPDATE Reminder r SET r.status = :cancelledStatus WHERE r.tenantId = :tenantId AND r.sourceType = :sourceType AND r.sourceId = :sourceId AND r.status = :pendingStatus")
    int cancelPendingByTenantIdAndSourceTypeAndSourceId(
        @Param("tenantId") UUID tenantId,
        @Param("sourceType") ReminderSourceType sourceType,
        @Param("sourceId") UUID sourceId,
        @Param("pendingStatus") ReminderStatus pendingStatus,
        @Param("cancelledStatus") ReminderStatus cancelledStatus
    );

    @Query(value = "SELECT * FROM reminders WHERE status = 'PENDING' AND scheduled_at <= now() AND (next_attempt_at IS NULL OR next_attempt_at <= now()) AND deleted = false ORDER BY scheduled_at ASC FOR UPDATE SKIP LOCKED LIMIT :batchSize", nativeQuery = true)
    List<Reminder> claimDueReminders(@Param("batchSize") int batchSize);
}
