CREATE TABLE workflow_node_idempotency (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    workflow_execution_id UUID NOT NULL,
    workflow_node_execution_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    result JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    last_error_code VARCHAR(100),
    last_error_message TEXT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_workflow_node_idempotency_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_workflow_node_idempotency_execution FOREIGN KEY (workflow_execution_id) REFERENCES workflow_executions(id),
    CONSTRAINT fk_workflow_node_idempotency_node_execution FOREIGN KEY (workflow_node_execution_id) REFERENCES workflow_node_executions(id),
    CONSTRAINT uq_workflow_node_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_workflow_node_idempotency_tenant
    ON workflow_node_idempotency(tenant_id, workflow_execution_id, workflow_node_execution_id);