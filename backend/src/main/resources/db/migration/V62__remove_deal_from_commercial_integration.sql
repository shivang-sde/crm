-- ============================================================================
-- V62__remove_deal_from_commercial_integration.sql
-- Commercial Integration V1 refactor: document sync no longer creates Deals.
-- Remove deal_id linkage; documents now associate only with Account+Contact.
-- Preserves existing CUSTOMER mappings and document rows; only drops Deal FK.
-- ============================================================================

-- Drop index first if exists (Postgres)
DROP INDEX IF EXISTS idx_ext_rec_deal;

-- Drop FK constraint if present, then column
-- Constraint name is auto-generated; use safe DO block
DO $$
BEGIN
  -- Drop FK referencing deals if exists
  IF EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE table_name = 'integration_external_records' AND constraint_type = 'FOREIGN KEY'
  ) THEN
    -- Attempt to drop known FK by column; ignore if not found
    BEGIN
      ALTER TABLE integration_external_records DROP CONSTRAINT IF EXISTS integration_external_records_deal_id_fkey;
    EXCEPTION WHEN undefined_object THEN NULL;
    END;
  END IF;
END$$;

ALTER TABLE integration_external_records DROP COLUMN IF EXISTS deal_id;

-- Ensure remaining indexes still present (no-op if already exists)
-- idx_ext_rec_tenant_type, idx_ext_rec_account, idx_ext_rec_contact remain.
