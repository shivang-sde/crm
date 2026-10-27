package com.shivang.crm.modules.ticket.entity;

import java.time.Instant;
import java.util.UUID;

import com.shivang.crm.shared.base.TenantOwnedEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * L3.1-P1: a tenant-owned customer-support record.
 *
 * <p>Extends {@link TenantOwnedEntity}, which is how every other tenant-owned
 * domain record in this repository is modelled (Deal, Offering,
 * CustomerEntitlement). No new base entity is introduced.
 *
 * <p>Relationship shape, fixed by existing evidence rather than preference:
 * {@code Contact.accountId} is NOT NULL, so Account is the root of any
 * customer-linked record. Therefore {@code accountId} is mandatory and
 * {@code contactId} is optional — a customer can report a problem without a named
 * contact on file. Account stays the canonical customer identity; Ticket never
 * becomes a competing one.
 */
@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_tickets_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_tickets_tenant_owner", columnList = "tenant_id, owner_user_id"),
        @Index(name = "idx_tickets_tenant_account", columnList = "tenant_id, account_id"),
        @Index(name = "idx_tickets_tenant_created_at", columnList = "tenant_id, created_at")
})
@AttributeOverride(name = "ownerId", column = @Column(name = "owner_user_id"))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket extends TenantOwnedEntity {

    /**
     * The canonical customer identity. Mandatory: a Ticket is customer
     * correspondence and cannot exist without one.
     */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Optional. When present, must belong to {@link #accountId}. */
    @Column(name = "contact_id")
    private UUID contactId;

    @Column(length = 255, nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private TicketStatus status;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    // ========== SLA (L3.1-P4) ==========

    /**
     * When an agent response is due. Snapshotted from the configured policy at
     * creation, never recalculated.
     */
    @Column(name = "response_due_at")
    private Instant responseDueAt;

    /** When a resolution is due. Snapshotted at creation alongside {@link #responseDueAt}. */
    @Column(name = "resolution_due_at")
    private Instant resolutionDueAt;

    /**
     * When the first agent message was recorded. Null until one exists, which is also
     * what makes the response SLA outstanding.
     */
    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    /**
     * Whether either deadline was missed. A single flag covers both response and
     * resolution because a consumer needs to know the obligation was missed, not which
     * clock ran out. This is historical: resolving, closing or reopening a Ticket
     * never clears it.
     */
    @Column(name = "sla_breached")
    private boolean slaBreached;

    /** {@code updated_by} is an audit column, not part of {@link TenantOwnedEntity}. */
    @Column(name = "updated_by")
    private UUID updatedBy;

    // ========== Lifecycle ==========

    /**
     * Applies a lifecycle transition and maintains the resolved/closed timestamps.
     *
     * <p>Entering {@code RESOLVED} stamps {@code resolvedAt}; entering {@code CLOSED}
     * stamps {@code closedAt}. Reopening clears both, because the previous
     * outcome no longer describes the ticket's state. There is deliberately no
     * status-history table: lifecycle history is represented by the existing
     * activity and canonical event infrastructure, which already record every
     * transition with its actor and timestamp.
     *
     * <p>The transition matrix itself is validated by
     * {@link TicketStatus#canTransitionTo} before this is called, so this method
     * only maintains the timestamps and never rejects.
     */
    public void applyStatus(TicketStatus newStatus, Instant now) {
        this.status = newStatus;

        switch (newStatus) {
            case RESOLVED -> {
                this.resolvedAt = now;
                this.closedAt = null;
            }
            case CLOSED -> {
                this.closedAt = now;
            }
            case OPEN, IN_PROGRESS -> {
                // Reopening or resuming work invalidates the previous outcome.
                this.resolvedAt = null;
                this.closedAt = null;
            }
        }
    }

    /** True when this ticket is a live, non-deleted record. */
    public boolean isActive() {
        return !isDeleted() && status != TicketStatus.CLOSED;
    }

    // ========== SLA (L3.1-P4) ==========

    /**
     * Stamps the policy snapshot at creation. Separate from creation itself so the
     * deadlines are derived from the caller's single notion of "now" rather than being
     * read from the clock twice.
     */
    public void applySlaPolicy(Instant createdAt, java.time.Duration responseDuration,
            java.time.Duration resolutionDuration) {
        this.responseDueAt = createdAt.plus(responseDuration);
        this.resolutionDueAt = createdAt.plus(resolutionDuration);
        this.slaBreached = false;
        this.firstResponseAt = null;
    }

    /**
     * Records the first agent response. Idempotent: only the first message wins, so a
     * later message cannot move the timestamp the response SLA was measured against.
     *
     * @return true when this call was the one that recorded it
     */
    public boolean recordFirstResponse(Instant at) {
        if (firstResponseAt != null) {
            return false;
        }
        this.firstResponseAt = at;
        return true;
    }

    /**
     * Whether the response SLA is still outstanding: no response yet, a deadline set,
     * and work still open. RESOLVED and CLOSED are excluded because neither is
     * awaiting a reply.
     */
    public boolean hasOutstandingResponseSla(Instant now) {
        return firstResponseAt == null
                && responseDueAt != null
                && !now.isBefore(responseDueAt)
                && isSlaEligible();
    }

    /** Whether the resolution SLA is still outstanding. */
    public boolean hasOutstandingResolutionSla(Instant now) {
        return resolutionDueAt != null
                && !now.isBefore(resolutionDueAt)
                && isSlaEligible();
    }

    /**
     * Whether this Ticket is still open work that an SLA can be missed on.
     *
     * <p>RESOLVED and CLOSED are excluded: a resolved Ticket is no longer awaiting
     * work, so missing its deadline afterwards is not a breach.
     */
    private boolean isSlaEligible() {
        return status != TicketStatus.RESOLVED && status != TicketStatus.CLOSED;
    }
}
