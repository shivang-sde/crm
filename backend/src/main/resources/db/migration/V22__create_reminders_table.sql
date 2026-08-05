-- ============================================================================
-- Reminder table for non-recurring reminders
-- ============================================================================

CREATE TABLE reminders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_id UUID NOT NULL,
    occurrence_at TIMESTAMP NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resolved_recipient_user_id UUID,
    title VARCHAR(255) NOT NULL,
    message TEXT,
    metadata JSONB,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    processed_at TIMESTAMP,
    next_attempt_at TIMESTAMP,
    processing_started_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT fk_reminders_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_reminders_tenant ON reminders(tenant_id);
CREATE INDEX idx_reminders_status_scheduled ON reminders(status, scheduled_at);
CREATE INDEX idx_reminders_tenant_source ON reminders(tenant_id, source_type, source_id);

CREATE UNIQUE INDEX uq_reminders_tenant_source_occurrence_scheduled
    ON reminders(tenant_id, source_type, source_id, occurrence_at, scheduled_at);
