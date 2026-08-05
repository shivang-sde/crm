package com.shivang.crm.modules.reminder.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "reminders",
    indexes = {
        @Index(name = "idx_reminders_tenant", columnList = "tenant_id"),
        @Index(name = "idx_reminders_status_scheduled", columnList = "status, scheduled_at"),
        @Index(name = "idx_reminders_tenant_source", columnList = "tenant_id, source_type, source_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_reminders_tenant_source_occurrence_scheduled",
            columnNames = {"tenant_id", "source_type", "source_id", "occurrence_at", "scheduled_at"}
        )
    }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Reminder extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20, nullable = false)
    private ReminderSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "occurrence_at", nullable = false)
    private Instant occurrenceAt;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ReminderStatus status = ReminderStatus.PENDING;

    @Column(name = "resolved_recipient_user_id")
    private UUID resolvedRecipientUserId;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(length = 255, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
}
