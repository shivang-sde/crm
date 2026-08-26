-- ============================================================================
-- Phase 8C-2B: Authoritative deal current-stage entry timestamp
-- ============================================================================

-- Authoritative timestamp for when the deal entered its CURRENT stage.
-- Maintained by the application on every actual stage change (PATCH /stage,
-- PUT /deals/{id} with stageId) and initialised on creation.
-- Application-maintained values are authoritative from this migration onward.
ALTER TABLE deals ADD COLUMN IF NOT EXISTS stage_entered_at TIMESTAMP;

-- Conservative backfill:
--   1) Latest STAGE_CHANGED entity_history row whose recorded newStageId
--      matches the deal's current stage -> use that transition timestamp.
--      NOTE: entity_history STAGE_CHANGED rows were historically written only
--      by the PATCH /stage path, so PUT-driven transitions have no such row.
--   2) Fallback: created_at (initial stage entry approximation).
-- For historical rows where the true stage-entry time cannot be known,
-- created_at is the explicit, documented fallback and is APPROXIMATE.
UPDATE deals d
SET stage_entered_at = COALESCE((
    SELECT h.created_at
    FROM entity_history h
    WHERE h.entity_type = 'DEAL'
      AND h.entity_id = d.id
      AND h.tenant_id = d.tenant_id
      AND h.event_type = 'STAGE_CHANGED'
      AND h.changes ->> 'newStageId' = d.stage_id::text
    ORDER BY h.created_at DESC
    LIMIT 1
), d.created_at)
WHERE d.stage_entered_at IS NULL;

-- No additional index added: current consumers load visible deals and compute
-- ageing in memory (Phase 8B/8C-2A/8C-2B aggregation pattern); no range query
-- filters on stage_entered_at yet. Revisit only if direct SQL filtering on
-- this column is introduced.
