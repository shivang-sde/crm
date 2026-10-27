-- ============================================================================
-- L3.1-P4: Ticket SLA fields
-- ============================================================================
--
-- Purpose
-- -------
-- Adds elapsed-time SLA obligations to the existing tickets table. No new table:
-- the Ticket remains the aggregate, and an SLA is four columns on it.
--
-- Why columns and not an sla_policies table
-- ----------------------------------------
-- The deadlines are a snapshot taken at creation and stored on the Ticket. Storing
-- the resolved deadlines rather than a reference to live policy is deliberate: if a
-- tenant later changes their response target, tickets raised under the old target
-- keep the obligation they were actually given. A mutable reference would silently
-- rewrite history.
--
-- Why there is no business-hours or holiday calendar
-- --------------------------------------------------
-- P4 measures elapsed wall-clock duration, so a 4-hour target is 4 hours whether or
-- not the business was open. That is a real simplification and it is the intended
-- one: a calendar would need per-tenant schedules, holiday tables, pause/resume
-- accounting and a recalculation story for tickets already overdue. None of that is
-- in scope. The column shape below is what a calendar would later have to coexist
-- with, so adding it now would not be smaller.
--
-- Why a single sla_breached boolean
-- --------------------------------
-- Response and resolution are separate deadlines but share one outcome a consumer
-- acts on: the obligation was missed. Adding response_sla_breached as a second
-- boolean would let the two disagree, and nothing in P4 needs to tell them apart
-- other than the event's breachType metadata.
--
-- sla_breached is historical. Resolving, closing or reopening a Ticket never
-- clears it — it records that the target was missed, which stays true.
--
-- Timestamp type
-- --------------
-- TIMESTAMP, matching the existing tickets columns (created_at, resolved_at,
-- closed_at) in V70. Instant values are already UTC-normalised at the application
-- boundary; a timezone-aware column here would imply a storage convention this
-- schema does not use elsewhere.
--
-- V70, V71 and V72 are not modified.
-- ============================================================================

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS response_due_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS resolution_due_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS first_response_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS sla_breached BOOLEAN NOT NULL DEFAULT FALSE;

-- Partial indexes, matching the V16 partial-index precedent in this schema.
--
-- The breach scanner's query is always: live, not yet breached, and past one of the
-- two deadlines. Both predicates are in the WHERE clause so the index holds only
-- the small set of rows that are actually candidates — including tickets that have
-- NOT breached, because they are still waiting to be scanned.
--
-- Response candidates additionally require first_response_at IS NULL: that is what
-- makes a response SLA outstanding, and leaving it out would return every
-- responded-to ticket forever.
CREATE INDEX IF NOT EXISTS idx_tickets_tenant_resolution_due_at
    ON tickets(tenant_id, resolution_due_at)
    WHERE deleted = FALSE AND sla_breached = FALSE;

CREATE INDEX IF NOT EXISTS idx_tickets_tenant_response_due_at
    ON tickets(tenant_id, response_due_at)
    WHERE deleted = FALSE AND sla_breached = FALSE AND first_response_at IS NULL;

-- ============================================================================
-- Deliberately absent
-- ============================================================================
--
-- No sla_policies, sla_rules, sla_targets, business_hours, holiday_calendars,
-- escalation_rules or ticket_sla table. P4's policy is two application-level
-- duration defaults snapshotted onto the Ticket.
--
-- No sla_policy_id, sla_policy_name or customer_sla_id: the Ticket stores resolved
-- deadlines, never a reference that could be edited out from under it.
--
-- No priority, category, source or channel. Priority-based targets would need a
-- priority column and a policy mapping, both out of scope.
--
-- No paused_at or total_paused_seconds: pause/resume accounting changes what
-- "elapsed" means, and P4's elapsed time is deliberately pause-free.
--
-- No breach_reason: the event carries breachType at the moment of breach, so a
-- reason column would only duplicate it into a row nothing reads.
--
-- No first_response_due_at or response_sla_breached: duplication of
-- response_due_at and sla_breached respectively.
--
-- Nothing here alters tickets' existing lifecycle columns. sla_breached does not
-- interact with status at the database level: the scanner excludes RESOLVED and
-- CLOSED in its query, and a Ticket that was already breached keeps the flag
-- whatever it does afterwards.
--
-- No idempotency caveat: ADD COLUMN IF NOT EXISTS makes re-application a no-op.
--
-- Verification (run after applying)
-- ---------------------------------
-- Expected columns: response_due_at, resolution_due_at, first_response_at,
-- sla_breached BOOLEAN NOT NULL DEFAULT FALSE.
--
-- Reversibility
-- -------------
-- ALTER TABLE tickets
--     DROP COLUMN IF EXISTS response_due_at,
--     DROP COLUMN IF EXISTS resolution_due_at,
--     DROP COLUMN IF EXISTS first_response_at,
--     DROP COLUMN IF EXISTS sla_breached;
-- DROP INDEX IF EXISTS idx_tickets_tenant_resolution_due_at;
-- DROP INDEX IF EXISTS idx_tickets_tenant_response_due_at;
-- ============================================================================
