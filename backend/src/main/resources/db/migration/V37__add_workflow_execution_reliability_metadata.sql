ALTER TABLE workflow_executions
    ADD COLUMN last_heartbeat_at TIMESTAMP,
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP,
    ADD COLUMN last_error_code VARCHAR(100),
    ADD COLUMN last_error_message TEXT;

ALTER TABLE workflow_node_executions
    ADD COLUMN last_heartbeat_at TIMESTAMP,
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP,
    ADD COLUMN last_error_code VARCHAR(100),
    ADD COLUMN last_error_message TEXT;

CREATE INDEX idx_workflow_executions_running_heartbeat
    ON workflow_executions(status, last_heartbeat_at)
    WHERE deleted = FALSE AND status = 'RUNNING';

CREATE INDEX idx_workflow_node_executions_running_heartbeat
    ON workflow_node_executions(status, last_heartbeat_at)
    WHERE deleted = FALSE AND status = 'RUNNING';