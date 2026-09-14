-- ============================================================================
-- WF-51: Webhook Idempotency & Ingestion Reliability
-- Store deliveries for idempotent webhook ingestion
-- ============================================================================

CREATE TABLE record_webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    webhook_id UUID NOT NULL REFERENCES record_webhooks(id) ON DELETE CASCADE,
    webhook_key VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(1024) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    record_id UUID REFERENCES records(id) ON DELETE SET NULL,
    response_status INT,
    response_body JSONB,
    error_code VARCHAR(100),
    error_message TEXT,
    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID
);

CREATE INDEX idx_webhook_deliveries_tenant_webhook ON record_webhook_deliveries(tenant_id, webhook_id);
CREATE INDEX idx_webhook_deliveries_tenant_key ON record_webhook_deliveries(tenant_id, webhook_id, idempotency_key);
CREATE INDEX idx_webhook_deliveries_received_at ON record_webhook_deliveries(received_at);

-- Partial unique index for idempotency: one delivery per (tenant, webhook, idempotency_key) when not deleted
CREATE UNIQUE INDEX uq_webhook_deliveries_idempotency
    ON record_webhook_deliveries (tenant_id, webhook_id, idempotency_key)
    WHERE deleted = false
      AND idempotency_key IS NOT NULL
      AND btrim(idempotency_key) <> '';
