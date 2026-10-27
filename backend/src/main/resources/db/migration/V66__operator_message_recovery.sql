-- ============================================================================
-- OPS-01 / OPS-02: Operator message recovery tooling
--
-- Adds the operator action audit trail and the two narrowly scoped permissions
-- that gate DLQ inspection / re-drive and outbox DEAD inspection / requeue.
--
-- Design constraints this migration must not violate:
--   * No automatic replay and no automatic resurrection. There is no scheduler,
--     no trigger and no listener here. Recovery is an explicit operator action
--     that inserts a row through the service layer.
--   * The partial unique index below is the database-enforced duplicate guard.
--     An in-memory set would not survive a restart or two concurrent operators.
-- ============================================================================

-- ── Operator action audit trail ─────────────────────────────────────────────
-- One table serves both DLQ re-drive and outbox DEAD requeue so there is a
-- single audit mechanism, not one per feature.
--
-- The raw message/event payload is deliberately NOT stored here. Columns carry
-- only identifiers and already-sanitised text.
CREATE TABLE IF NOT EXISTS operator_message_actions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- DLQ_REDRIVE | OUTBOX_REQUEUE
    action_type              VARCHAR(40)  NOT NULL,

    -- Idempotency target. For DLQ_REDRIVE this is the original AMQP message id
    -- (stable: the publisher sets it to the canonical event id). For
    -- OUTBOX_REQUEUE this is crm_event_outbox.event_id, as text.
    target_id                VARCHAR(255) NOT NULL,

    -- Platform operators have no tenant; NULL means platform scope.
    tenant_id                UUID,

    operator_user_id         UUID        NOT NULL,
    operator_role            VARCHAR(100),

    -- Inbound request correlation, from X-Request-Id / the audit request id.
    request_id               VARCHAR(255),

    -- Source routing, as verified from RabbitMQ x-death metadata or the outbox row.
    original_exchange        VARCHAR(255),
    original_routing_key     VARCHAR(255),
    destination_exchange     VARCHAR(255),
    destination_routing_key  VARCHAR(255),

    -- SUCCESS | FAILED | CONFLICT | NOT_FOUND | NOT_ALLOWED
    result                   VARCHAR(40)  NOT NULL,

    -- Sanitised, length-capped. Never a raw payload, credential or token.
    failure_reason           VARCHAR(500),

    created_at               TIMESTAMP    NOT NULL,
    updated_at               TIMESTAMP    NOT NULL,
    deleted                  BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at               TIMESTAMP,
    deleted_by               UUID
);

-- Duplicate-re-drive / duplicate-requeue guard.
--
-- Partial on result = 'SUCCESS' so failed attempts are recorded for forensics but
-- do not block a later legitimate attempt. A message id that has already been
-- successfully re-driven can never be re-driven again without an explicit
-- operator deleting the audit row - this is the interlock that prevents an
-- unbounded DLQ -> re-drive -> DLQ loop.
CREATE UNIQUE INDEX IF NOT EXISTS uq_operator_action_success_target
    ON operator_message_actions (action_type, target_id)
    WHERE deleted = FALSE AND result = 'SUCCESS';

CREATE INDEX IF NOT EXISTS idx_operator_action_created
    ON operator_message_actions (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_operator_action_tenant
    ON operator_message_actions (tenant_id, action_type, created_at DESC);

-- Any attempt on a target, most recent first (drives the "already re-driven?" check
-- and the operator history view).
CREATE INDEX IF NOT EXISTS idx_operator_action_target
    ON operator_message_actions (action_type, target_id, created_at DESC);

-- ── Permissions ────────────────────────────────────────────────────────────
-- Follows the V25 / V28 / V40 / V64 seeding convention: fixed UUIDs,
-- ON CONFLICT (module, action) DO NOTHING.
--
-- module 'admin' is chosen deliberately: it is already seeded and already in
-- DefaultRoleConfig.ADMIN_MODULES, so tenant ADMIN roles created after this
-- migration pick the permissions up automatically via the tenant trigger below.
INSERT INTO permissions (id, module, action, description)
VALUES
    ('6f0a0c11-0000-4000-8000-000000000001', 'admin', 'operations_read',
     'Inspect failed asynchronous work: RabbitMQ dead letters and terminal outbox events'),
    ('6f0a0c11-0000-4000-8000-000000000002', 'admin', 'operations_write',
     'Explicitly recover failed asynchronous work: re-drive a dead letter or requeue a terminal outbox event')
ON CONFLICT (module, action) DO NOTHING;

-- Backfill SUPERADMIN (platform): full visibility across all tenants.
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
JOIN permissions p ON p.module = 'admin' AND p.action IN ('operations_read', 'operations_write')
WHERE r.name = 'SUPERADMIN'
  AND r.level = 'PLATFORM'
  AND r.tenant_id IS NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Backfill every existing tenant ADMIN.
--
-- Scope is ALL, but the service layer still restricts a tenant caller to rows
-- whose tenant_id equals the caller's tenant. The scope only grants access to the
-- capability, not to other tenants' rows.
--
-- MANAGER and EMPLOYEE are deliberately NOT granted: recovering failed
-- asynchronous work is an operator action, not a day-to-day CRM permission.
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
JOIN permissions p ON p.module = 'admin' AND p.action IN ('operations_read', 'operations_write')
WHERE r.name = 'ADMIN'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Extend the tenant provisioning trigger so newly created tenant ADMIN roles get
-- the new permissions. Identical to V64's function with the operations actions
-- appended to the ADMIN grant; 'admin' is already in the module list, so only the
-- action filter is new.
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

        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT admin_role_id, p.id, 'ALL'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'report', 'workflow', 'admin', 'offering', 'entitlement', 'acquisition', 'records')
        ON CONFLICT (role_id, permission_id) DO NOTHING;

        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT manager_role_id, p.id, 'TEAM'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'report', 'offering', 'entitlement', 'acquisition')
          AND p.action IN ('read', 'write', 'assign', 'export')
        ON CONFLICT (role_id, permission_id) DO NOTHING;

        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT employee_role_id, p.id, 'OWN'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'entitlement')
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

DROP TRIGGER IF EXISTS tenant_creation_trigger ON tenants;
CREATE TRIGGER tenant_creation_trigger
    AFTER INSERT ON tenants
    FOR EACH ROW
    EXECUTE FUNCTION create_tenant_default_roles();
