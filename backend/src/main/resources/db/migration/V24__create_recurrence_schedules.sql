-- ============================================================================
-- Create recurrence schedule state table for future recurrence processing
-- ============================================================================

CREATE TABLE recurrence_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_id UUID NOT NULL,
    recurrence JSONB,
    initial_occurrence_at TIMESTAMP NOT NULL,
    last_occurrence_at TIMESTAMP,
    next_occurrence_at TIMESTAMP,
    reminder_offset_seconds BIGINT,
    generated_occurrence_count INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT uk_recurrence_schedules_source UNIQUE (tenant_id, source_type, source_id),
    CONSTRAINT fk_recurrence_schedules_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_recurrence_schedules_tenant ON recurrence_schedules(tenant_id);
CREATE INDEX idx_recurrence_schedules_next_occurrence ON recurrence_schedules(next_occurrence_at);
