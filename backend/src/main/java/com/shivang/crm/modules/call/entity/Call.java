package com.shivang.crm.modules.call.entity;

import java.time.Instant;
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
    @Builder.Default
    private CallType callType = CallType.OUTGOING;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    // Polymorphic linking to any entity
    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    @Builder.Default
    private CallStatus status = CallStatus.PLANNED;

    // Reminder
    @Column(name = "remind_at")
    private Instant remindAt;

    // Recurrence (stored as JSONB)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Recurrence recurrence;

    // Custom data (JSONB for extensibility)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> customData;

    // Helper methods
    public void markAsHeld() {
        this.status = CallStatus.HELD;
        if (this.startTime != null && this.endTime != null) {
            long diffMillis = this.endTime.toEpochMilli() - this.startTime.toEpochMilli();
            this.durationMinutes = (int) (diffMillis / 60000);
        }
    }

    public void markAsNotHeld() {
        this.status = CallStatus.NOT_HELD;
    }

    public void cancel() {
        this.status = CallStatus.CANCELLED;
    }

    public boolean isCompleted() {
        return this.status == CallStatus.HELD || this.status == CallStatus.NOT_HELD;
    }

    public enum CallType {
        INCOMING, OUTGOING
    }

    public enum CallStatus {
        PLANNED, HELD, NOT_HELD, CANCELLED
    }
}
