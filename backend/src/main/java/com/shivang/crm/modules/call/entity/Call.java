package com.shivang.crm.modules.call.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.TenantOwnedEntity;
import com.shivang.crm.shared.model.Recurrence;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "calls")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@AttributeOverrides({
    @AttributeOverride(name = "ownerId", column = @Column(name = "owner_user_id"))
})
public class Call extends TenantOwnedEntity {

    @Column(length = 255, nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Call details
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private CallType callType = CallType.OUTGOING;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "recording_url", columnDefinition = "TEXT")
    private String recordingUrl;

    @Column(length = 100)
    private String disposition;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 100)
    private String nextAction;

    @Column(name = "follow_up_at")
    private Instant followUpAt;

    // Polymorphic linking to any entity
    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private CallStatus status = CallStatus.PLANNED;

    // Reminder
    @Column(name = "remind_at")
    private Instant remindAt;

     @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "actor_type", nullable = false, length = 30)
    private String actorType = "USER";

@Column(name = "actor_source", length = 100)
private String actorSource;

    // Recurrence (stored as JSONB)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Recurrence recurrence;

    // Custom data (JSONB for extensibility)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> customData;

    // Helper methods
    public void markAsHeld(UUID userId) {
        this.status = CallStatus.HELD;
        this.updatedBy = userId;
        if (this.startTime != null && this.endTime != null) {
            this.durationMinutes = (int) ChronoUnit.MINUTES.between(startTime, endTime);
        }
    }

    public void markAsNotHeld(UUID userId) {
        this.status = CallStatus.NOT_HELD;
        this.updatedBy = userId;
    }

    public void cancel(UUID userId) {
        this.status = CallStatus.CANCELLED;
        this.updatedBy = userId;
    }

    public boolean isCompleted() {
        return this.status == CallStatus.HELD || this.status == CallStatus.NOT_HELD;
    }

    public boolean isScheduled() {
        return this.status == CallStatus.PLANNED;
    }

    // Optional: Add to each entity if you want entity-specific soft delete
    @Override
    public void softDelete(UUID deletedBy) {
        this.setDeleted(true);
        this.setDeletedAt(Instant.now());
        this.setDeletedBy(deletedBy);
        this.updatedBy = deletedBy;
    }


    public enum CallType {
        INCOMING, OUTGOING
    }

    public enum CallStatus {
        PLANNED, HELD, NOT_HELD, CANCELLED
    }
}
