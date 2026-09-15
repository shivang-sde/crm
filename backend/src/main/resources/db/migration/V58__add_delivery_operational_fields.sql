-- ============================================================================
-- WF-57: Operational visibility for webhook deliveries
-- Add correlation fields to enrich existing delivery record without new table
-- ============================================================================

ALTER TABLE record_webhook_deliveries
    ADD COLUMN IF NOT EXISTS record_type_id UUID,
    ADD COLUMN IF NOT EXISTS mapping_profile_id UUID,
    ADD COLUMN IF NOT EXISTS event_id UUID,
    ADD COLUMN IF NOT EXISTS failure_stage VARCHAR(30);

-- Failure stage values: RECEIVED, AUTHENTICATION, VALIDATION, MAPPING, RECORD_CREATION, EVENT_PUBLICATION, SUCCESS
-- Kept as VARCHAR to avoid enum migration complexity; app validates allowed values.

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_event_id ON record_webhook_deliveries(event_id) WHERE event_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_record_type ON record_webhook_deliveries(record_type_id) WHERE record_type_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_status ON record_webhook_deliveries(status);
