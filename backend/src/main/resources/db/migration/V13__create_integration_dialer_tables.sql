-- ============================================================================
-- Integration and Dialer Foundation Tables
-- Version: 1.0
-- Description: Creates provider-neutral integration and dialer foundation tables
-- ============================================================================

CREATE TABLE provider_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_key VARCHAR(100) NOT NULL,
    provider_name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL DEFAULT 'COMMUNICATION',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    default_config JSONB,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT uq_provider_definitions_key UNIQUE (provider_key)
);

CREATE INDEX idx_provider_definitions_active ON provider_definitions(is_active);
CREATE INDEX idx_provider_definitions_category ON provider_definitions(category);

CREATE TABLE provider_action_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID NOT NULL,
    action_key VARCHAR(100) NOT NULL,
    action_name VARCHAR(200) NOT NULL,
    description TEXT,
    endpoint_template TEXT,          
    headers_template TEXT,          
    http_method VARCHAR(50), 
    request_template JSONB,
    response_template JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT fk_provider_action_definitions_provider FOREIGN KEY (provider_id) REFERENCES provider_definitions(id) ON DELETE CASCADE,
    CONSTRAINT uq_provider_action_definitions_key UNIQUE (provider_id, action_key)
);

CREATE INDEX idx_provider_action_definitions_provider ON provider_action_definitions(provider_id);
CREATE INDEX idx_provider_action_definitions_active ON provider_action_definitions(is_active);

CREATE TABLE provider_trigger_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID NOT NULL,
    trigger_key VARCHAR(100) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT fk_provider_trigger_definitions_provider FOREIGN KEY (provider_id) REFERENCES provider_definitions(id) ON DELETE CASCADE,
    CONSTRAINT uq_provider_trigger_definitions_key UNIQUE (provider_id, trigger_key)
);

CREATE INDEX idx_provider_trigger_definitions_provider ON provider_trigger_definitions(provider_id);
CREATE INDEX idx_provider_trigger_definitions_active ON provider_trigger_definitions(is_active);

CREATE TABLE connector_instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    provider_id UUID NOT NULL,
    connector_name VARCHAR(200) NOT NULL,
    environment VARCHAR(50) NOT NULL DEFAULT 'SANDBOX',
    base_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    config JSONB,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT fk_connector_instances_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_connector_instances_provider FOREIGN KEY (provider_id) REFERENCES provider_definitions(id) ON DELETE CASCADE
);

CREATE INDEX idx_connector_instances_tenant ON connector_instances(tenant_id);
CREATE INDEX idx_connector_instances_provider ON connector_instances(provider_id);
CREATE INDEX idx_connector_instances_active ON connector_instances(tenant_id, is_active);

CREATE TABLE connector_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_instance_id UUID NOT NULL,
    credential_name VARCHAR(200) NOT NULL,
    auth_type VARCHAR(50) NOT NULL DEFAULT 'API_KEY',
    encrypted_value TEXT,
    metadata JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT fk_connector_credentials_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_connector_credentials_connector FOREIGN KEY (connector_instance_id) REFERENCES connector_instances(id) ON DELETE CASCADE
);

CREATE INDEX idx_connector_credentials_tenant ON connector_credentials(tenant_id);
CREATE INDEX idx_connector_credentials_connector ON connector_credentials(connector_instance_id);
CREATE INDEX idx_connector_credentials_active ON connector_credentials(tenant_id, is_active);

CREATE TABLE connector_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_instance_id UUID NOT NULL,
    connector_credential_id UUID,
    action_key VARCHAR(100) NOT NULL,
    execution_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    request_payload JSONB,
    response_payload JSONB,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT fk_connector_executions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_connector_executions_connector FOREIGN KEY (connector_instance_id) REFERENCES connector_instances(id) ON DELETE CASCADE,
    CONSTRAINT fk_connector_executions_credential FOREIGN KEY (connector_credential_id) REFERENCES connector_credentials(id) ON DELETE SET NULL
);

CREATE INDEX idx_connector_executions_tenant ON connector_executions(tenant_id);
CREATE INDEX idx_connector_executions_connector ON connector_executions(connector_instance_id);
CREATE INDEX idx_connector_executions_status ON connector_executions(tenant_id, execution_status);
CREATE INDEX idx_connector_executions_created_at ON connector_executions(created_at DESC);

CREATE TABLE connector_webhook_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_instance_id UUID NOT NULL,
    webhook_name VARCHAR(200) NOT NULL,
    target_url TEXT NOT NULL,
    verification_secret TEXT,
    verification_mode VARCHAR(50) DEFAULT 'HEADER',
    event_types JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT fk_connector_webhook_configs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_connector_webhook_configs_connector FOREIGN KEY (connector_instance_id) REFERENCES connector_instances(id) ON DELETE CASCADE
);

CREATE INDEX idx_connector_webhook_configs_tenant ON connector_webhook_configs(tenant_id);
CREATE INDEX idx_connector_webhook_configs_connector ON connector_webhook_configs(connector_instance_id);
CREATE INDEX idx_connector_webhook_configs_active ON connector_webhook_configs(tenant_id, is_active);

CREATE TABLE connector_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_instance_id UUID NOT NULL,
    connector_execution_id UUID,
    external_event_id VARCHAR(255),
    external_reference_id VARCHAR(255),
    event_type VARCHAR(100) NOT NULL,
    verification_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    processing_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    event_payload JSONB,
    error_message TEXT,
    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT fk_connector_webhook_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_connector_webhook_events_connector FOREIGN KEY (connector_instance_id) REFERENCES connector_instances(id) ON DELETE CASCADE,
    CONSTRAINT fk_connector_webhook_events_execution FOREIGN KEY (connector_execution_id) REFERENCES connector_executions(id) ON DELETE SET NULL
);

CREATE INDEX idx_connector_webhook_events_tenant ON connector_webhook_events(tenant_id);
CREATE INDEX idx_connector_webhook_events_connector ON connector_webhook_events(connector_instance_id);
CREATE INDEX idx_connector_webhook_events_external_reference ON connector_webhook_events(external_reference_id);
CREATE INDEX idx_connector_webhook_events_event_type ON connector_webhook_events(event_type);
CREATE INDEX idx_connector_webhook_events_received_at ON connector_webhook_events(received_at DESC);
CREATE UNIQUE INDEX uq_connector_webhook_events_external_event ON connector_webhook_events(connector_instance_id, external_event_id) WHERE external_event_id IS NOT NULL AND deleted = false;

CREATE TABLE call_provider_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    call_id UUID NOT NULL,
    connector_execution_id UUID,
    provider_id UUID,
    external_call_id VARCHAR(255),
    external_agent_id VARCHAR(255),
    correlation_key VARCHAR(255),
    linked_at TIMESTAMP NOT NULL DEFAULT NOW(),
    metadata JSONB,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT fk_call_provider_links_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_call_provider_links_call FOREIGN KEY (call_id) REFERENCES calls(id) ON DELETE CASCADE,
    CONSTRAINT fk_call_provider_links_execution FOREIGN KEY (connector_execution_id) REFERENCES connector_executions(id) ON DELETE SET NULL,
    CONSTRAINT fk_call_provider_links_provider FOREIGN KEY (provider_id) REFERENCES provider_definitions(id) ON DELETE SET NULL
);

CREATE INDEX idx_call_provider_links_tenant ON call_provider_links(tenant_id);
CREATE INDEX idx_call_provider_links_call ON call_provider_links(call_id);
CREATE INDEX idx_call_provider_links_external_call_id ON call_provider_links(external_call_id);
CREATE INDEX idx_call_provider_links_correlation_key ON call_provider_links(correlation_key);

CREATE TABLE call_connect_triggers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_instance_id UUID,
    trigger_key VARCHAR(100) NOT NULL,
    call_direction VARCHAR(20) NOT NULL DEFAULT 'BOTH',
    open_action_type VARCHAR(50) NOT NULL DEFAULT 'ENTITY_DETAIL',
    entity_type VARCHAR(50),
    entity_resolve_by VARCHAR(50),
    target_route VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 0,
    config JSONB,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT fk_call_connect_triggers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_call_connect_triggers_connector FOREIGN KEY (connector_instance_id) REFERENCES connector_instances(id) ON DELETE SET NULL
);

CREATE INDEX idx_call_connect_triggers_tenant ON call_connect_triggers(tenant_id);
CREATE INDEX idx_call_connect_triggers_connector ON call_connect_triggers(connector_instance_id);
CREATE INDEX idx_call_connect_triggers_active ON call_connect_triggers(tenant_id, is_active);

CREATE TABLE call_layout_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_instance_id UUID,
    layout_name VARCHAR(200) NOT NULL,
    display_mode VARCHAR(50) NOT NULL DEFAULT 'MODAL',
    layout_config JSONB,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT fk_call_layout_configs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_call_layout_configs_connector FOREIGN KEY (connector_instance_id) REFERENCES connector_instances(id) ON DELETE SET NULL
);

CREATE INDEX idx_call_layout_configs_tenant ON call_layout_configs(tenant_id);
CREATE INDEX idx_call_layout_configs_connector ON call_layout_configs(connector_instance_id);
CREATE INDEX idx_call_layout_configs_active ON call_layout_configs(tenant_id, is_active);
CREATE INDEX idx_call_layout_configs_default ON call_layout_configs(tenant_id, is_default);
