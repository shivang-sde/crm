-- ============================================================================
-- L3.1-P3: Ticket Message conversation
-- ============================================================================
--
-- Purpose
-- -------
-- A TicketMessage is one entry in a Ticket's conversation: an agent's note or a
-- reply intended for the customer. It is a child of Ticket and nothing else.
--
-- Why there is no account_id / contact_id / owner_id here
-- -------------------------------------------------------
-- Those columns belong to the parent Ticket, and duplicating them would create two
-- copies that can disagree. A message claiming a different account than the Ticket
-- it hangs from is an integrity bug waiting to happen, and the same applies to
-- ownership: a TicketMessage has no independent business ownership, so its record
-- scope is inherited entirely from the parent Ticket. This is also why the entity
-- extends BaseEntity rather than TenantOwnedEntity — there is no owner_id column to
-- populate, and inventing one would create a second ownership model that could
-- drift from the Ticket's.
--
-- Why visibility is not a delivery mechanism
-- ------------------------------------------
-- visibility is INTERNAL or CUSTOMER_VISIBLE and nothing more. P3 implements no
-- email, SMS, WhatsApp, portal or notification of any kind, so a
-- CUSTOMER_VISIBLE row records intent only. There is no delivery status, no
-- delivered_at, and no attempt/outbox table for outbound messages, because there is
-- no outbound path.
--
-- Why messages survive a closed Ticket
-- -----------------------------------
-- Closing a Ticket freezes its conversation; it does not destroy it. There is no
-- ON DELETE CASCADE from tickets, and TicketService never cascades. A support
-- conversation is an audit record of what was said to a customer, so it is
-- retained.
--
-- Soft delete
-- ----------
-- deleted / deleted_at / deleted_by follow the repository-wide convention
-- (BaseEntity). Messages are never physically deleted: TicketMessageRepository
-- exposes no hard-delete finder and no cascade remove.
--
-- Tenant safety
-- -------------
-- Every query against ticket_messages is tenant-scoped AND deleted = false. The
-- foreign keys below are deliberately TENANT-BLIND, exactly like every other
-- customer-domain FK in this schema (deals.account_id, tickets.account_id,
-- customer_entitlements.deal_id). The FK therefore prevents a missing parent but
-- cannot prevent a cross-tenant one, so TicketMessageService resolves both the
-- parent Ticket and the author through tenant-scoped lookups. That is the only
-- real defence, and it is why this comment exists.
--
-- Idempotency
-- -----------
-- CREATE TABLE IF NOT EXISTS. Unlike V71 this migration seeds no permissions and
-- adds no tables beyond the one required, so it has nothing to double-apply.
--
-- Reversibility
-- -------------
-- DROP TABLE ticket_messages;
-- ============================================================================

CREATE TABLE IF NOT EXISTS ticket_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id UUID NOT NULL,

    ticket_id UUID NOT NULL,

    author_user_id UUID NOT NULL,

    body TEXT NOT NULL,

    visibility VARCHAR(30) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_ticket_messages_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_ticket_messages_ticket_id FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT fk_ticket_messages_author_user_id FOREIGN KEY (author_user_id) REFERENCES users(id),
    CONSTRAINT fk_ticket_messages_deleted_by FOREIGN KEY (deleted_by) REFERENCES users(id)
);

-- The one query P3 actually issues: a ticket's live conversation, newest first.
-- deleted is deliberately NOT in this index: the service filters it, and adding it
-- would widen a four-column index for a boolean that is false on almost every row.
-- Index selectivity here comes from (tenant_id, ticket_id), which is already narrow.
CREATE INDEX IF NOT EXISTS idx_ticket_messages_tenant_ticket
    ON ticket_messages(tenant_id, ticket_id, created_at);

-- Supports author-scoped lookups (has this agent written on this ticket?) without
-- scanning the tenant's whole conversation history.
CREATE INDEX IF NOT EXISTS idx_ticket_messages_tenant_author
    ON ticket_messages(tenant_id, author_user_id);

-- ============================================================================
-- Deliberately absent
-- ============================================================================
--
-- No ticket_message_history, ticket_message_events, ticket_message_attachments,
-- ticket_sla, ticket_escalations, or delivery/outbox table. Message audit lives in
-- the existing activities table and canonical event outbox, exactly as Ticket
-- lifecycle does — P3 introduces no second audit store.
--
-- No ticket priority, category, source or channel column. Those belong to the Ticket
-- aggregate, not to a single message, and are out of scope for P3.
--
-- No CHECK constraint on visibility. @Enumerated(STRING) plus the Java enum is the
-- single source of truth, and a CHECK would need updating in lockstep or a later
-- phase's value would be rejected at the database rather than in the domain.
-- The same reasoning applies to V70's status column.
-- ============================================================================
