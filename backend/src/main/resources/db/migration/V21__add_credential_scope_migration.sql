ALTER TABLE connector_credentials
    ADD COLUMN IF NOT EXISTS credential_scope VARCHAR(30),
    ADD COLUMN IF NOT EXISTS owner_user_id UUID;

UPDATE connector_credentials
SET credential_scope =
    CASE
        WHEN created_by IS NULL THEN 'TENANT'
        ELSE 'USER'
    END
WHERE credential_scope IS NULL;

ALTER TABLE connector_credentials
    ALTER COLUMN credential_scope SET NOT NULL;

ALTER TABLE connector_credentials
    ADD CONSTRAINT fk_connector_credentials_owner_user
        FOREIGN KEY (owner_user_id)
        REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_connector_credentials_user_resolution
    ON connector_credentials (
        tenant_id,
        connector_instance_id,
        owner_user_id,
        is_active
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_user_connector_credential
    ON connector_credentials (
        tenant_id,
        connector_instance_id,
        owner_user_id
    )
    WHERE credential_scope = 'USER'
      AND is_active = TRUE
      AND deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_tenant_connector_credential
    ON connector_credentials (
        tenant_id,
        connector_instance_id
    )
    WHERE credential_scope = 'TENANT'
      AND is_active = TRUE
      AND deleted = FALSE;


CREATE TABLE connector_user_agents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id UUID NOT NULL,
    connector_instance_id UUID NOT NULL,
    user_id UUID NOT NULL,

    external_agent_id VARCHAR(150),
    external_agent_number VARCHAR(100),

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID,

    CONSTRAINT fk_connector_user_agents_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenants(id),

    CONSTRAINT fk_connector_user_agents_connector
        FOREIGN KEY (connector_instance_id)
        REFERENCES connector_instances(id),

    CONSTRAINT fk_connector_user_agents_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT ck_connector_user_agents_identifier
        CHECK (
            external_agent_id IS NOT NULL
            OR external_agent_number IS NOT NULL
        )
);

CREATE INDEX idx_connector_user_agents_tenant
    ON connector_user_agents (tenant_id)
    WHERE deleted = FALSE;

CREATE INDEX idx_connector_user_agents_connector
    ON connector_user_agents (
        tenant_id,
        connector_instance_id
    )
    WHERE deleted = FALSE;

CREATE INDEX idx_connector_user_agents_user_lookup
    ON connector_user_agents (
        tenant_id,
        connector_instance_id,
        user_id
    )
    WHERE deleted = FALSE
      AND is_active = TRUE;

CREATE UNIQUE INDEX uq_connector_user_agents_user
    ON connector_user_agents (
        tenant_id,
        connector_instance_id,
        user_id
    )
    WHERE deleted = FALSE;

CREATE UNIQUE INDEX uq_connector_user_agents_external_id
    ON connector_user_agents (
        tenant_id,
        connector_instance_id,
        external_agent_id
    )
    WHERE external_agent_id IS NOT NULL
      AND deleted = FALSE;

CREATE UNIQUE INDEX uq_connector_user_agents_external_number
    ON connector_user_agents (
        tenant_id,
        connector_instance_id,
        external_agent_number
    )
    WHERE external_agent_number IS NOT NULL
      AND deleted = FALSE;