-- Durable canonical CRM event outbox. Payloads contain canonical events only.
CREATE TABLE crm_event_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_name VARCHAR(150) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL DEFAULT NOW(),
    next_attempt_at TIMESTAMP,
    processing_started_at TIMESTAMP,
    published_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT uq_crm_event_outbox_event_id UNIQUE (event_id),
    CONSTRAINT fk_crm_event_outbox_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_crm_event_outbox_pending ON crm_event_outbox(status, available_at)
    WHERE deleted = FALSE AND status IN ('PENDING', 'PROCESSING');

CREATE INDEX idx_crm_event_outbox_tenant_entity
    ON crm_event_outbox(tenant_id, aggregate_type, aggregate_id);