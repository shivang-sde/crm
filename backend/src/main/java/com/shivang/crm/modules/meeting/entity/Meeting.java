package com.shivang.crm.modules.meeting.entity;

import java.time.Instant;
import java.util.List;
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
@Table(name = "meetings")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@AttributeOverrides({
    @AttributeOverride(name = "ownerId", column = @Column(name = "owner_user_id"))
})
public class Meeting extends TenantOwnedEntity {

    @Column(length = 255, nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String agenda;

    // Location (address or video link)
    @Column(columnDefinition = "TEXT")
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, name = "meeting_type")
    private MeetingType meetingType;

    // Timing
    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    // Attendees (JSON array of emails or contact IDs)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> attendees;

    // Polymorphic linking to any entity
    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    @Builder.Default
    private MeetingStatus status = MeetingStatus.PLANNED;

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
        this.status = MeetingStatus.HELD;
    }

    public void markAsNotHeld() {
        this.status = MeetingStatus.NOT_HELD;
    }

    public void cancel() {
        this.status = MeetingStatus.CANCELLED;
    }

    public boolean isCompleted() {
        return this.status == MeetingStatus.HELD || this.status == MeetingStatus.NOT_HELD;
    }

    public enum MeetingType {
        IN_PERSON, VIDEO, PHONE
    }

    public enum MeetingStatus {
        PLANNED, HELD, NOT_HELD, CANCELLED
    }
}
