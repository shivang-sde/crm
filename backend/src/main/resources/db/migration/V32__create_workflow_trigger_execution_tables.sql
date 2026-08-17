-- Minimal workflow trigger and execution persistence for canonical CRM events.
CREATE TABLE workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_workflows_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_workflows_tenant_status ON workflows(tenant_id, status)
    WHERE deleted = FALSE;

CREATE TABLE workflow_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    workflow_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    trigger_entity_type VARCHAR(100) NOT NULL,
    trigger_event_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_workflow_versions_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_workflow_versions_workflow_id FOREIGN KEY (workflow_id) REFERENCES workflows(id),
    CONSTRAINT uq_workflow_versions_workflow_number UNIQUE (workflow_id, version_number)
);

CREATE INDEX idx_workflow_versions_trigger
    ON workflow_versions(tenant_id, trigger_entity_type, trigger_event_type, status)
    WHERE deleted = FALSE;

CREATE TABLE workflow_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    workflow_id UUID NOT NULL,
    workflow_version_id UUID NOT NULL,
    trigger_event_id UUID NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    trigger_context JSONB,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_workflow_executions_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_workflow_executions_workflow_id FOREIGN KEY (workflow_id) REFERENCES workflows(id),
    CONSTRAINT fk_workflow_executions_version_id FOREIGN KEY (workflow_version_id) REFERENCES workflow_versions(id),
    CONSTRAINT uq_workflow_executions_version_event UNIQUE (workflow_version_id, trigger_event_id)
);

CREATE INDEX idx_workflow_executions_tenant_status ON workflow_executions(tenant_id, status);