-- BE-WF-8: workflow execution causal lineage for cross-workflow loop safety.
-- Root executions (created directly from domain events) keep the defaults:
--   caused_by_execution_id = NULL, caused_by_event_id = trigger event, chain_depth = 0.
-- Action-caused executions inherit lineage from the causing execution.

ALTER TABLE workflow_executions
    ADD COLUMN caused_by_execution_id UUID,
    ADD COLUMN caused_by_event_id UUID,
    ADD COLUMN chain_depth INT NOT NULL DEFAULT 0;

ALTER TABLE workflow_executions
    ADD CONSTRAINT fk_workflow_executions_caused_by
    FOREIGN KEY (caused_by_execution_id) REFERENCES workflow_executions(id);
