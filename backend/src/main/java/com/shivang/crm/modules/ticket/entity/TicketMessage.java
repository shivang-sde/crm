package com.shivang.crm.modules.ticket.entity;

import java.util.UUID;

import com.shivang.crm.shared.base.BaseEntity;

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
 * L3.1-P3: a message in a Ticket's conversation.
 *
 * <p><b>Extends {@link BaseEntity}, not {@code TenantOwnedEntity}.</b> This is the
 * one deliberate departure from the Ticket aggregate's own shape, and it is the
 * central design decision in P3. A Ticket message has no independent business
 * ownership: it cannot be worked, assigned, escalated or reported on in isolation.
 * Access to a message is entirely inherited from its parent Ticket, so giving it an
 * {@code ownerId} would create a second ownership model that could disagree with
 * the first — exactly the kind of drift a record boundary must not have.
 *
 * <p>Consequently this entity has no {@code accountId}, {@code contactId} or
 * {@code ownerId}. Those belong to the parent Ticket and duplicating them would
 * create two copies that can disagree: a message claiming a different account than
 * the Ticket it hangs from is an integrity bug waiting to happen. The parent is the
 * single source of customer identity, record scope, tenant relationship and
 * lifecycle state.
 *
 * <p>{@code tenantId} is declared explicitly rather than inherited, because it must
 * be present on every tenant-sensitive read and there is no owner field to hang a
 * column off. It is still the same JPA property name as on every other tenant-owned
 * entity, so the column is {@code tenant_id} as everywhere else.
 *
 * <p>{@code authorUserId} is not {@code createdBy}. {@code createdBy} would mean
 * "who created this row", which happens to be the author for messages today, but
 * {@code authorUserId} is the domain fact P3 authorizes against: only the author may
 * edit or delete their own message. Naming it for the authorization role rather than
 * the audit role keeps that rule readable.
 *
 * <p>Messages survive their Ticket being closed. Closing a Ticket freezes the
 * conversation, it does not destroy it, and no cascade delete exists.
 */
@Entity
@Table(name = "ticket_messages", indexes = {
        // The one query P3 actually issues: a ticket's live conversation, newest first.
        @Index(name = "idx_ticket_messages_tenant_ticket", columnList = "tenant_id, ticket_id, created_at"),
        // Supports "has this author already written on this ticket?" style checks and
        // keeps author lookups from scanning the tenant's whole conversation history.
        @Index(name = "idx_ticket_messages_tenant_author", columnList = "tenant_id, author_user_id")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessage extends BaseEntity {

    /**
     * The tenant that owns this message. Always equal to the parent Ticket's tenant,
     * which {@code TicketMessageService} enforces on every write.
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Parent Ticket. The FK is tenant-blind like every other FK in this schema, so it
     * prevents a missing parent but cannot prevent a cross-tenant one — the service
     * resolves the parent through the tenant-scoped finder instead.
     */
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    /**
     * The agent who wrote this message, always the authenticated caller. Never
     * client-supplied: P3 rejects author impersonation at the service boundary.
     *
     * <p>Also the author-only authorization fact. Only this user may edit or delete
     * the message, regardless of their scope over the parent Ticket.
     */
    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    /**
     * Who the message is addressed to. Records intent only; P3 performs no delivery,
     * so {@link TicketMessageVisibility#CUSTOMER_VISIBLE} does not imply the customer
     * has received or will receive anything.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private TicketMessageVisibility visibility = TicketMessageVisibility.INTERNAL;

    }
