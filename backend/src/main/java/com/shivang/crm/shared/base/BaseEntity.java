package com.shivang.crm.shared.base;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Boolean deleted = false;
    private Instant deletedAt;
    private UUID deletedBy;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;

        if (this.deleted == null) {
            this.deleted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }


    // ========== Explicit Setter Methods for Soft Delete ==========
    
    /**
     * Set deleted status
     */
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    /**
     * Set deleted at timestamp
     */
    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    /**
     * Set deleted by user
     */
    public void setDeletedBy(UUID deletedBy) {
        this.deletedBy = deletedBy;
    }

    /**
     * Soft delete this entity
     */
    public void softDelete(UUID deletedBy) {
        this.deleted = true;
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
    }

    /**
     * Restore a soft-deleted entity
     */
    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
        this.deletedBy = null;
    }

    /**
     * Check if entity is deleted
     */
    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }
}
