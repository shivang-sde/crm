-- Dedicated encrypted credential storage for Outbound HTTP connections.
-- ConnectorCredential rows are bound to connector instances (NOT NULL), so
-- outbound HTTP connections need their own credential records that reuse the
-- existing AES-256-GCM CredentialEncryptionService for values at rest.
CREATE TABLE outbound_http_connection_credentials (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    auth_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    encrypted_value TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);

CREATE INDEX idx_outbound_http_conn_cred_tenant
    ON outbound_http_connection_credentials (tenant_id);
