-- ============================================================================
-- Harden lead ingestion idempotency: DB-enforced uniqueness
-- ============================================================================
-- Existing flow relied on application-level lookup before insert, leaving a
-- race window for concurrent duplicate webhooks. This migration enforces
-- uniqueness at the database level for the idempotency identity:
--   (tenant_id, ingestion_config_id, idempotency_key)
-- Only non-empty, non-deleted keys participate. Null/blank keys remain
-- non-unique (each webhook without a key is a distinct event).

-- Step 1: deduplicate existing rows that would violate the new index.
-- Keep the earliest event per (tenant, config, idempotency_key); null out
-- later duplicates so history is preserved but uniqueness is restorable.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY tenant_id, ingestion_config_id, idempotency_key
               ORDER BY received_at ASC, created_at ASC
           ) AS rn
    FROM lead_ingestion_events
    WHERE deleted = false
      AND idempotency_key IS NOT NULL
      AND btrim(idempotency_key) <> ''
)
UPDATE lead_ingestion_events
SET idempotency_key = NULL
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

-- Step 2: partial unique index enforcing idempotency at the DB level.
CREATE UNIQUE INDEX IF NOT EXISTS uq_lead_ingestion_events_idempotency
    ON lead_ingestion_events (tenant_id, ingestion_config_id, idempotency_key)
    WHERE deleted = false
      AND idempotency_key IS NOT NULL
      AND btrim(idempotency_key) <> '';
