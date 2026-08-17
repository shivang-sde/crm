CREATE TABLE workflow_node_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    workflow_execution_id UUID NOT NULL,
    workflow_node_id UUID NOT NULL,
    node_key VARCHAR(150) NOT NULL,
    node_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_code VARCHAR(100),
    error_message TEXT,
    input_context JSONB,
    output_context JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_workflow_node_executions_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_workflow_node_executions_execution_id FOREIGN KEY (workflow_execution_id) REFERENCES workflow_executions(id),
    CONSTRAINT fk_workflow_node_executions_node_id FOREIGN KEY (workflow_node_id) REFERENCES workflow_nodes(id),
    CONSTRAINT uq_workflow_node_executions_execution_node UNIQUE (workflow_execution_id, workflow_node_id)
);

CREATE INDEX idx_workflow_node_executions_execution
    ON workflow_node_executions(workflow_execution_id);
CREATE INDEX idx_workflow_node_executions_tenant_status
    ON workflow_node_executions(tenant_id, status);
CREATE INDEX idx_workflow_node_executions_node
    ON workflow_node_executions(workflow_node_id);

ALTER TABLE workflow_executions
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(100);