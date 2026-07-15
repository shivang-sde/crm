-- Add indexes to improve lookups for call opening events
-- Partial indexes (WHERE deleted = false) used because V15 added a soft-delete `deleted` column

CREATE INDEX IF NOT EXISTS idx_call_opening_events_call_id ON call_opening_events(call_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_call_opening_events_external_call_id ON call_opening_events(external_call_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_call_opening_events_delivery_status ON call_opening_events(delivery_status) WHERE deleted = false;

-- Tenant-scoped lookup helpers
CREATE INDEX IF NOT EXISTS idx_call_opening_events_tenant_delivery_status ON call_opening_events(tenant_id, delivery_status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_call_opening_events_tenant_user_delivery_status ON call_opening_events(tenant_id, user_id, delivery_status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_call_opening_events_tenant_agent_delivery_status ON call_opening_events(tenant_id, agent_id, delivery_status) WHERE deleted = false;
