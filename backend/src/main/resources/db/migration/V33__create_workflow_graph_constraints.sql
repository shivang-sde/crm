CREATE TABLE workflow_nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    workflow_version_id UUID NOT NULL,
    node_key VARCHAR(150) NOT NULL,
    node_type VARCHAR(30) NOT NULL,
    name VARCHAR(200) NOT NULL,
    configuration JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_workflow_nodes_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_workflow_nodes_version_id FOREIGN KEY (workflow_version_id) REFERENCES workflow_versions(id)
);

CREATE UNIQUE INDEX uq_workflow_nodes_version_key
    ON workflow_nodes(workflow_version_id, node_key)
    WHERE deleted = FALSE;

CREATE INDEX idx_workflow_nodes_tenant_version
    ON workflow_nodes(tenant_id, workflow_version_id)
    WHERE deleted = FALSE;

CREATE TABLE workflow_edges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    workflow_version_id UUID NOT NULL,
    source_node_id UUID NOT NULL,
    target_node_id UUID NOT NULL,
    edge_key VARCHAR(150),
    configuration JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_workflow_edges_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_workflow_edges_version_id FOREIGN KEY (workflow_version_id) REFERENCES workflow_versions(id),
    CONSTRAINT fk_workflow_edges_source_node_id FOREIGN KEY (source_node_id) REFERENCES workflow_nodes(id),
    CONSTRAINT fk_workflow_edges_target_node_id FOREIGN KEY (target_node_id) REFERENCES workflow_nodes(id)
);

CREATE INDEX idx_workflow_edges_tenant_version
    ON workflow_edges(tenant_id, workflow_version_id)
    WHERE deleted = FALSE;

CREATE INDEX idx_workflow_edges_source ON workflow_edges(source_node_id) WHERE deleted = FALSE;
CREATE INDEX idx_workflow_edges_target ON workflow_edges(target_node_id) WHERE deleted = FALSE;

CREATE UNIQUE INDEX uq_workflow_versions_active
    ON workflow_versions(workflow_id)
    WHERE status = 'ACTIVE' AND deleted = FALSE;