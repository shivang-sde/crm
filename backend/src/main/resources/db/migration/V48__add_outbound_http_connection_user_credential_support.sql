-- Outbound HTTP connections: allow per-user credentials (like ConnectorCredential USER/TENANT scope)
-- Existing rows: tenant-scoped (owner_user_id NULL, credential_scope TENANT) — preserved for backward compat.
ALTER TABLE outbound_http_connection_credentials
    ADD COLUMN owner_user_id UUID,
    ADD COLUMN credential_scope VARCHAR(16) NOT NULL DEFAULT 'TENANT',
    ADD COLUMN connection_id UUID;

-- Backfill existing rows: set connection_id from outbound_http_connections.credential_id and ensure TENANT scope
UPDATE outbound_http_connection_credentials c
SET connection_id = conn.id,
    credential_scope = 'TENANT',
    owner_user_id = NULL
FROM outbound_http_connections conn
WHERE conn.credential_id = c.id;

-- For credentials not yet linked (should be none), keep as is

-- Index for tenant+connection+user lookup (used by OutboundHttpServiceImpl user-aware resolution)
CREATE INDEX idx_outbound_http_cred_tenant_connection_user
    ON outbound_http_connection_credentials (tenant_id, connection_id, owner_user_id)
    WHERE deleted = FALSE AND is_active = TRUE;

CREATE INDEX idx_outbound_http_cred_tenant_user
    ON outbound_http_connection_credentials (tenant_id, owner_user_id)
    WHERE deleted = FALSE AND is_active = TRUE;

-- Optional FK (soft, not enforced to avoid hard delete issues; tenant isolation enforced in code)
-- ALTER TABLE outbound_http_connection_credentials
--     ADD CONSTRAINT fk_outbound_http_cred_connection FOREIGN KEY (connection_id) REFERENCES outbound_http_connections(id);
-- ALTER TABLE outbound_http_connection_credentials
--     ADD CONSTRAINT fk_outbound_http_cred_owner_user FOREIGN KEY (owner_user_id) REFERENCES users(id);

-- Optional FK (soft, not enforced to avoid hard delete issues; tenant isolation enforced in code)
-- ALTER TABLE outbound_http_connection_credentials
--     ADD CONSTRAINT fk_outbound_http_cred_owner_user FOREIGN KEY (owner_user_id) REFERENCES users(id);
