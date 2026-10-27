-- ============================================================================
-- L3.3-P1: Customer Feedback foundation
-- ============================================================================
-- Voice of Customer signals (NPS / CSAT / CES / general / complaint) submitted
-- against the canonical customer identity.
--
-- Identity
-- --------
-- account_id is NOT NULL. Feedback expresses an opinion about an existing
-- customer, so the row is anchored exactly like contacts.account_id and
-- tickets.account_id, and deliberately unlike deals.account_id and
-- customer_entitlements.account_id which are nullable because those records may
-- pre-date a customer relationship.
--
-- External callers never send account_id or contact_id: they send an email,
-- which the service resolves to an existing Account/Contact inside their tenant.
-- The submitted email is NOT stored here — Account and Contact already own it,
-- and duplicating customer PII would create a second place for it to go stale.
--
-- Ticket correlation
-- ------------------
-- ticket_id is optional and nullable on purpose. NPS, CSAT and CES are each
-- collectable independently of any support ticket, and a P1 complaint may exist
-- with no ticket at all; ticket lifecycle behaviour is L3.3-P2.
--
-- No deal_id, no entitlement_id
-- -----------------------------
-- Nothing in L3.3-P1 needs them, and an unused nullable foreign key is a second
-- half-truth about who a customer is. Per-entitlement effort is an L3.4/L3.5
-- concern.
--
-- No status column, no classification column
-- -------------------------------------------
-- Feedback is immutable after submission: there is no lifecycle and no derived
-- band is stored. NPS promoter/detractor and CES effort bands are computed at
-- read time. This matches the decision recorded for tickets in V73 -- the event
-- that produced a value already records it, and a stored derived column needs a
-- backfill the day a scale changes.
--
-- Tenant safety
-- -------------
-- Every foreign key below is TENANT-BLIND, exactly like every other
-- customer-domain FK in this schema (deals.account_id, customer_entitlements.deal_id,
-- tickets.account_id). CustomerRelationshipValidator is what stops a known UUID
-- from another tenant being attached: requireLiveAccount / requireLiveContact for
-- identity, requireContactBelongsToAccount and requireTicketBelongsToAccount for
-- the correlated rows.
--
-- No CHECK constraint on type or source, and none on score
-- --------------------------------------------------------
-- @Enumerated(EnumType.STRING) plus the Java enum is the single source of truth,
-- matching the decision recorded in V72 for ticket_messages.visibility: a CHECK
-- would need updating in lockstep or a later phase's value would be rejected by
-- the database rather than by the domain. Score bounds depend on `type` (NPS 0-10,
-- CSAT 1-5, CES 1-7) and live on FeedbackType, so a CHECK here would be a second
-- copy of that scale. The service rejects an out-of-range score rather than
-- clamping it.


CREATE TABLE IF NOT EXISTS feedback (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id           UUID NOT NULL,

    -- Canonical customer identity. Feedback always belongs to a customer.
    account_id          UUID NOT NULL,

    -- Optional correlation. Both are validated against account_id at the service
    -- boundary; neither is behaviour in P1.
    contact_id          UUID,
    ticket_id           UUID,

    type                VARCHAR(30) NOT NULL,
    score               SMALLINT,
    comment             TEXT,

    -- Provenance, not an integration mechanism. Part of the dedup key below.
    source              VARCHAR(30),

    -- The source system's stable submission identifier, when it has one.
    external_reference  VARCHAR(255),

    owner_user_id       UUID,
    created_by          UUID NOT NULL,
    updated_by          UUID,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    deleted             BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          UUID,

    CONSTRAINT fk_feedback_tenant_id    FOREIGN KEY (tenant_id)    REFERENCES tenants(id),
    CONSTRAINT fk_feedback_account_id   FOREIGN KEY (account_id)   REFERENCES accounts(id),
    CONSTRAINT fk_feedback_contact_id   FOREIGN KEY (contact_id)   REFERENCES contacts(id),
    CONSTRAINT fk_feedback_ticket_id    FOREIGN KEY (ticket_id)    REFERENCES tickets(id),
    CONSTRAINT fk_feedback_owner_id     FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT fk_feedback_created_by   FOREIGN KEY (created_by)   REFERENCES users(id),
    CONSTRAINT fk_feedback_updated_by   FOREIGN KEY (updated_by)   REFERENCES users(id),
    CONSTRAINT fk_feedback_deleted_by   FOREIGN KEY (deleted_by)   REFERENCES users(id)
);


-- ---------------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------------
-- Each one is here for a specific access pattern, not for symmetry.

-- The account-scoped list, and later the L3.3-P4 Customer 360 feedback section.
-- Leads with tenant so it can also serve a tenant-wide newest-first scan.
CREATE INDEX IF NOT EXISTS idx_feedback_tenant_account_created
    ON feedback (tenant_id, account_id, created_at DESC);

-- Per-type metric aggregation: NPS/CSAT/CES averages, and the NPS promoter /
-- detractor split. L3.3-P5.
CREATE INDEX IF NOT EXISTS idx_feedback_tenant_type_created
    ON feedback (tenant_id, type, created_at DESC);

-- "Show feedback for this ticket". Partial, because most rows carry no ticket
-- and indexing the NULLs would bloat the index for no query.
CREATE INDEX IF NOT EXISTS idx_feedback_tenant_ticket
    ON feedback (tenant_id, ticket_id)
    WHERE ticket_id IS NOT NULL;

-- FeedbackIdentityResolver resolves a submitted email against contacts and then
-- against accounts. contacts.email already has idx_contact_email and the
-- uq_contact_email partial unique index from V9; accounts.email has neither, so
-- the fallback lookup would otherwise be a sequential scan of the table.
CREATE INDEX IF NOT EXISTS idx_account_email
    ON accounts (lower(email))
    WHERE email IS NOT NULL;


-- ---------------------------------------------------------------------------
-- External-reference idempotency
-- ---------------------------------------------------------------------------
-- tenant + source + externalReference + active = one accepted response.
--
-- PARTIAL, so an unrelated submission with no external reference never collides,
-- and so soft-deleting a feedback row releases its reference for genuine reuse.
-- The blank-string guard matches normalize() in FeedbackService: a whitespace-only
-- reference is treated as absent, so it must not occupy the slot either.
--
-- This partial form is the same pattern as uq_contact_email (V9) and
-- uq_lead_ingestion_events_idempotency (V45). It is deliberately NOT a plain
-- table-level UNIQUE: customer_entitlements.deal_line_item shows what happens when
-- a non-partial constraint meets a soft delete -- the slot stays occupied and a
-- legitimate re-create fails with a constraint violation instead of succeeding.
CREATE UNIQUE INDEX IF NOT EXISTS uq_feedback_external_reference
    ON feedback (tenant_id, source, external_reference)
    WHERE deleted = false
      AND source IS NOT NULL
      AND external_reference IS NOT NULL
      AND btrim(external_reference) <> '';


-- ---------------------------------------------------------------------------
-- Permissions
-- ---------------------------------------------------------------------------
-- Exactly two. Feedback is immutable after submission, so there is no delete
-- action; it is not routed work, so there is no assign action (a complaint's
-- routing belongs to its Ticket); and nothing reviews it in P1, so there is no
-- review action. Export belongs with the L3.3-P5 analytics surface.
--
-- A new 4-character prefix is reserved here; none may be reused.
INSERT INTO permissions (id, module, action, description)
VALUES
    ('bbbb2222-bbbb-bbbb-bbbb-000000000001', 'feedback', 'read',
     'View customer feedback records'),
    ('bbbb2222-bbbb-bbbb-bbbb-000000000002', 'feedback', 'write',
     'Submit customer feedback')
ON CONFLICT (module, action) DO NOTHING;


-- ---------------------------------------------------------------------------
-- Backfill: existing tenant roles
-- ---------------------------------------------------------------------------
-- Both seeding paths are required. The backfills below fix tenants that already
-- exist; the create_tenant_default_roles() re-declaration at the bottom fixes
-- tenants created after this migration. Miss either and you get a split-brain
-- state where some tenants can read feedback and some cannot.

-- SUPERADMIN: all feedback permissions. Also covered by the platform bypass in
-- PermissionEvaluatorService, but seeded so the catalog is complete.
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
JOIN permissions p
  ON p.module = 'feedback'
WHERE r.name = 'SUPERADMIN'
  AND r.level = 'PLATFORM'
  AND r.tenant_id IS NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ADMIN: all feedback permissions.
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
JOIN permissions p
  ON p.module = 'feedback'
WHERE r.name = 'ADMIN'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- MANAGER: read/write at TEAM, matching this repo's existing MANAGER grants.
-- The action filter is stated even though both feedback actions qualify, so the
-- grant stays correct if a third action is added later.
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'TEAM'
FROM roles r
JOIN permissions p
  ON p.module = 'feedback'
WHERE r.name = 'MANAGER'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
  AND p.action IN ('read', 'write')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- EMPLOYEE: read/write at OWN. An employee may submit feedback for a customer
-- they own and read back only their own. Employee holds no assign or delete
-- action for any module, and feedback has no delete action to hold.
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'OWN'
FROM roles r
JOIN permissions p
  ON p.module = 'feedback'
WHERE r.name = 'EMPLOYEE'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
  AND p.action IN ('read', 'write')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ---------------------------------------------------------------------------
-- Tenant default roles: tenants provisioned from here on
-- ---------------------------------------------------------------------------
-- Replaces the function so a NEW tenant's roles include 'feedback'. Module and
-- action sets must stay in sync with DefaultRoleConfig.ADMIN_MODULES /
-- MANAGER_MODULES / EMPLOYEE_MODULES and the *_ACTIONS sets.
CREATE OR REPLACE FUNCTION create_tenant_default_roles()
RETURNS TRIGGER AS $$
DECLARE
    admin_role_id UUID;
    manager_role_id UUID;
    employee_role_id UUID;
BEGIN
    BEGIN
        INSERT INTO roles (id, name, level, tenant_id, parent_role_id, description)
        VALUES (gen_random_uuid(), 'ADMIN', 'TENANT', NEW.id, NULL,
                'Tenant administrator - full access to all tenant records')
        RETURNING id INTO admin_role_id;

        INSERT INTO roles (id, name, level, tenant_id, parent_role_id, description)
        VALUES (gen_random_uuid(), 'MANAGER', 'TENANT', NEW.id, NULL,
                'Manager - access to team records')
        RETURNING id INTO manager_role_id;

        INSERT INTO roles (id, name, level, tenant_id, parent_role_id, description)
        VALUES (gen_random_uuid(), 'EMPLOYEE', 'TENANT', NEW.id, NULL,
                'Employee - own records only')
        RETURNING id INTO employee_role_id;

        -- ADMIN: every permission in every module, including feedback.
        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT admin_role_id, p.id, 'ALL'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'report', 'workflow', 'admin', 'offering', 'entitlement', 'acquisition', 'records',
                          'ticket', 'feedback')
        ON CONFLICT (role_id, permission_id) DO NOTHING;

        -- MANAGER: feedback joins the team-readable modules.
        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT manager_role_id, p.id, 'TEAM'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'report', 'offering', 'entitlement', 'acquisition', 'ticket',
                          'feedback')
          AND p.action IN ('read', 'write', 'assign', 'export')
        ON CONFLICT (role_id, permission_id) DO NOTHING;

        -- EMPLOYEE: explicitly {read, write}. 'assign' is absent by construction.
        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT employee_role_id, p.id, 'OWN'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'entitlement', 'ticket',
                          'feedback')
          AND p.action IN ('read', 'write')
        ON CONFLICT (role_id, permission_id) DO NOTHING;

        RAISE NOTICE 'Created default roles (ADMIN, MANAGER, EMPLOYEE) for tenant %', NEW.id;

    EXCEPTION WHEN OTHERS THEN
        INSERT INTO trigger_errors (trigger_name, table_name, record_id, error_message, error_detail)
        VALUES (
            'trigger_create_tenant_roles',
            'tenants',
            NEW.id,
            SQLERRM,
            SQLSTATE
        );
        RAISE WARNING 'Error creating default roles for tenant %: % (SQLSTATE: %)',
                      NEW.id, SQLERRM, SQLSTATE;
    END;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
