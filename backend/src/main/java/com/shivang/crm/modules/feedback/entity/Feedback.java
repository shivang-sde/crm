package com.shivang.crm.modules.feedback.entity;

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
 * L3.3-P1: one submitted customer signal, anchored on the canonical customer identity.
 *
 * <p>Account is the anchor and is mandatory. Feedback expresses an opinion about an
 * existing customer, so an NPS answer from a lead that has not been converted is a
 * lead-qualification signal (already modelled as {@code Lead.score}) rather than a
 * customer-experience one. This is the same distinction the schema already draws:
 * {@code contacts.account_id} and {@code tickets.account_id} are NOT NULL, while
 * {@code deals.account_id} and {@code customer_entitlements.account_id} are nullable
 * precisely because those records may pre-date a customer relationship.
 *
 * <p>{@code contactId} and {@code ticketId} are optional correlation, both validated
 * to belong to {@code accountId}. Contact is optional because a score may be given
 * about the relationship rather than a person; Ticket is optional because NPS, CSAT
 * and CES are each collectable independently of any support ticket.
 *
 * <p>Ownership defaults to the parent Account's owner rather than to whoever
 * submitted the row. External feedback frequently has no natural submitting CRM
 * user, and without an owner the shared {@code OWN}/{@code TEAM}/{@code ALL}
 * record-scope specifications would have nothing to filter on. Inheriting the
 * Account's owner makes Feedback respond to the same scope rules as every other
 * customer-domain record, with no join and no second scope model.
 *
 * <p>Immutable after submission. There is no status column and no update path in
 * P1: a submitted customer score is a statement of fact, and silently editing one
 * would corrupt every average derived from it.
 *
 * <p>{@code tenantId} plus the single-column, tenant-blind foreign keys are the
 * repository's established shape (see the note in {@code V70__create_tickets.sql});
 * cross-tenant integrity is enforced by {@code CustomerRelationshipValidator} at
 * the service boundary, not by these constraints.
 */
@Entity
@Table(
        name = "feedback",
        indexes = {
                // The Account-scoped list and the L3.3-P4 Customer 360 section.
                @Index(name = "idx_feedback_tenant_account_created",
                        columnList = "tenant_id, account_id, created_at"),
                // Per-type metric aggregation: NPS/CSAT/CES averages and counts.
                @Index(name = "idx_feedback_tenant_type_created",
                        columnList = "tenant_id, type, created_at"),
                // "Show feedback for this ticket". Partial because most rows have none.
                @Index(name = "idx_feedback_tenant_ticket",
                        columnList = "tenant_id, ticket_id")
        }
)
// V74 names this column owner_user_id. Without the override, Hibernate maps the
// inherited ownerId to the default owner_id, so every scope predicate that asks
// for root.get("ownerId") fails with "column owner_id does not exist". The other
// tenant-owned entities (Deal, Account, Contact, Ticket) all carry this override.
@AttributeOverride(name = "ownerId", column = @Column(name = "owner_user_id"))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Feedback extends TenantOwnedEntity {

    /** Required: Account remains the canonical customer identity. */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Optional: who was asked. Must belong to {@link #accountId}. */
    @Column(name = "contact_id")
    private UUID contactId;

    /**
     * Optional: the interaction that prompted this feedback. Must belong to
     * {@link #accountId}. No Ticket behaviour is implied — creating one is P2.
     */
    @Column(name = "ticket_id")
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private FeedbackType type;

    /**
     * Range-validated against {@link FeedbackType}. Not clamped: an out-of-range
     * value is rejected rather than adjusted.
     */
    @Column(name = "score")
    private Integer score;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    /** Provenance. Part of the external-reference deduplication key. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 30)
    private FeedbackSource source;

    /**
     * The source system's stable submission identifier, when it has one. Together
     * with {@code source} it makes a resubmission idempotent. Optional, and
     * uniqueness is enforced by a partial index that ignores NULL and blank values
     * so unrelated submissions never collide.
     */
    @Column(name = "external_reference", length = 255)
    private String externalReference;

    /**
     * Not on {@code TenantOwnedEntity}, matching every other domain entity: the
     * base class supplies createdBy/ownerId but each entity declares updatedBy.
     */
    @Column(name = "updated_by")
    private UUID updatedBy;
}
