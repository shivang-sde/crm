package com.shivang.crm.modules.task.entity;

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
@Table(name = "tasks")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@AttributeOverrides({
    @AttributeOverride(name = "ownerId", column = @Column(name = "owner_user_id"))
})
public class Task extends TenantOwnedEntity {

    @Column(length = 255, nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date")
    private Instant dueDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private TaskStatus status = TaskStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TaskPriority priority = TaskPriority.MEDIUM;

    // Polymorphic linking to any entity
    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    // Reminder
    @Column(name = "remind_at")
    private Instant remindAt;

    // Recurrence (stored as JSONB)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Recurrence recurrence;

    // Completion tracking
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "is_closed")
    private Boolean isClosed = false;

    // Assigned user
    // @Column(name = "assigned_to")
    // private UUID assignedTo;

    @Column(name = "updated_by")
    private UUID updatedBy;


    // Custom data (JSONB for extensibility)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> customData;

    // Helper methods
    public void complete(UUID userId) {
        this.status = TaskStatus.COMPLETED;
        this.isClosed = true;
        this.completedAt = Instant.now();
        this.updatedBy = userId;
    }

     public void reopen(UUID userId) {
        this.status = TaskStatus.NOT_STARTED;
        this.isClosed = false;
        this.completedAt = null;
        this.updatedBy = userId;
    }

    @Override
    public void softDelete(UUID deletedBy) {
        super.softDelete(deletedBy); // Call BaseEntity's softDelete
        this.updatedBy = deletedBy;
    }

    // Optional: Add to each entity if you want entity-specific soft delete

    public boolean isOverdue() {
        if (this.isClosed || this.dueDate == null) {
            return false;
        }
        return Instant.now().isAfter(this.dueDate);
    }
    public boolean isCompleted() {
        return this.status == TaskStatus.COMPLETED;
    }

    public static boolean isStatusClosed(TaskStatus status) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
