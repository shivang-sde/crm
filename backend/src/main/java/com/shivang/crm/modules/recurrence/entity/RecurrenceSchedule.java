package com.shivang.crm.modules.recurrence.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.modules.reminder.entity.ReminderSourceType;
import com.shivang.crm.shared.base.BaseEntity;
import com.shivang.crm.shared.model.Recurrence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Entity
@Table(
    name = "recurrence_schedules",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_recurrence_schedules_source", columnNames = {"tenant_id", "source_type", "source_id"})
    }
)
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RecurrenceSchedule extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private ReminderSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recurrence", columnDefinition = "jsonb")
    private Recurrence recurrence;

    @Column(name = "initial_occurrence_at", nullable = false)
    private Instant initialOccurrenceAt;

    @Column(name = "last_occurrence_at")
    private Instant lastOccurrenceAt;

    @Column(name = "next_occurrence_at")
    private Instant nextOccurrenceAt;

    @Column(name = "generated_occurrence_count")
    @Builder.Default
    private Integer generatedOccurrenceCount = 1;

    @Column(name = "reminder_offset_seconds")
    private Long reminderOffsetSeconds;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}
