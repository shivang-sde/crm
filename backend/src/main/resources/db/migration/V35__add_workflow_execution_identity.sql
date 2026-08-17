ALTER TABLE workflow_executions
    ADD COLUMN actor_id UUID,
    ADD COLUMN actor_type VARCHAR(30);

CREATE INDEX idx_workflow_executions_actor
    ON workflow_executions(tenant_id, actor_id, actor_type);