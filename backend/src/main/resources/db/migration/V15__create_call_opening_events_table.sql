-- Create table for call opening events
CREATE TABLE IF NOT EXISTS call_opening_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID,
    agent_id VARCHAR(255),
    call_id UUID,
    external_call_id VARCHAR(255),
    provider_key VARCHAR(100),
    trigger_key VARCHAR(100),
    instruction JSONB,
    delivery_status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    delivered_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID 
);

CREATE INDEX IF NOT EXISTS idx_call_opening_events_tenant ON call_opening_events(tenant_id);
CREATE INDEX IF NOT EXISTS idx_call_opening_events_agent ON call_opening_events(agent_id);
CREATE INDEX IF NOT EXISTS idx_call_opening_events_user ON call_opening_events(user_id);
