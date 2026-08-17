CREATE TABLE outbound_http_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    auth_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    credential_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_outbound_http_connections_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_outbound_http_connections_credential FOREIGN KEY (credential_id) REFERENCES connector_credentials(id)
);

CREATE INDEX idx_outbound_http_connections_tenant_active
    ON outbound_http_connections(tenant_id, active)
    WHERE deleted = FALSE;