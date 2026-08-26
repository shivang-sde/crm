-- WORKFLOW-7.5: track which execution a replay execution was created from.
ALTER TABLE workflow_executions
    ADD COLUMN replayed_from_execution_id UUID,
    ADD CONSTRAINT fk_workflow_executions_replayed_from FOREIGN KEY (replayed_from_execution_id) REFERENCES workflow_executions(id);

CREATE INDEX idx_workflow_executions_replayed_from
    ON workflow_executions(replayed_from_execution_id)
    WHERE replayed_from_execution_id IS NOT NULL;
