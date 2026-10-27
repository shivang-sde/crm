package com.shivang.crm.modules.feedback.entity;

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
 * L3.5: a single-use invitation to score one ticket.
 *
 * <p>The account/contact are snapshots taken from the ticket at creation, so a
 * later ticket edit cannot silently move an outstanding request to another
 * customer. The public submission still re-checks that the ticket is live and
 * still belongs to the snapshot account/contact.
 */
@Entity
@Table(
        name = "feedback_requests",
        indexes = {
                @Index(name = "idx_feedback_requests_tenant_ticket_created",
                        columnList = "tenant_id, ticket_id, created_at"),
                @Index(name = "idx_feedback_requests_pending",
                        columnList = "tenant_id, ticket_id, expires_at")
        }
)
@AttributeOverride(name = "ownerId", column = @Column(name = "owner_user_id"))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest extends TenantOwnedEntity {

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private FeedbackType type;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "feedback_id")
    private UUID feedbackId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public FeedbackRequestStatus statusAt(Instant now) {
        return FeedbackRequestStatus.at(submittedAt, revokedAt, expiresAt, now);
    }

    public boolean isPendingAt(Instant now) {
        return statusAt(now) == FeedbackRequestStatus.PENDING;
    }

    public void revoke(UUID actorId, Instant now) {
        this.revokedAt = now;
        this.updatedBy = actorId;
    }

    public void markSubmitted(UUID feedbackId, UUID actorId, Instant now) {
        this.feedbackId = feedbackId;
        this.submittedAt = now;
        this.updatedBy = actorId;
    }
}
