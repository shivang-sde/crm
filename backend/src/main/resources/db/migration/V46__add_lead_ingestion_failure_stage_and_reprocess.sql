-- ============================================================================
-- LEAD-ING-2: Operational round-trip — failure stage + reprocess support
-- ============================================================================

ALTER TABLE lead_ingestion_events
    ADD COLUMN IF NOT EXISTS failure_stage VARCHAR(30),
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 1;

-- Backfill existing rows: infer stage from current status/error
UPDATE lead_ingestion_events
SET failure_stage = CASE
    WHEN status = 'DUPLICATE' THEN 'DEDUPLICATION'
    WHEN status = 'REJECTED' THEN 'VALIDATION'
    WHEN status = 'FAILED' THEN 'UNKNOWN'
    ELSE NULL
END
WHERE failure_stage IS NULL
  AND status IN ('DUPLICATE', 'REJECTED', 'FAILED');

CREATE INDEX IF NOT EXISTS idx_lead_ingestion_events_failure_stage
    ON lead_ingestion_events(tenant_id, failure_stage);
