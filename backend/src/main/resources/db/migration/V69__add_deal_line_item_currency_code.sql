-- ============================================================================
-- L3.0-P4-F: deal_line_items.currency_code
-- ============================================================================
--
-- Purpose
-- -------
-- DealLineItem carried monetary values (unit_price, discount_amount,
-- tax_amount, line_total) with no currency provenance, while Offering and
-- CustomerEntitlement both already have an optional currency_code. This adds
-- the same optional field to the line item and backfills it from the Offering
-- the line was sold from.
--
-- Nullability
-- -----------
-- Offering.currency_code is itself nullable (V25), and OfferingService only
-- normalises it when the caller supplies one. This column is therefore
-- nullable, matching the Offering rather than defaulting to an invented
-- currency. A NULL here means "the Offering had no currency configured", which
-- is the honest representation of that state.
--
-- Idempotency
-- -----------
-- ADD COLUMN IF NOT EXISTS, and the backfill is guarded by IS NULL so re-running
-- cannot overwrite a value that has since been set by the application.
--
-- Tenant safety
-- -------------
-- The backfill joins on both tenant_id and offering_id, so a line item can only
-- ever inherit currency from an Offering in its own tenant.
--
-- Reversibility
-- -------------
-- DROP COLUMN currency_code removes the field; no other object depends on it.
-- ============================================================================

ALTER TABLE deal_line_items
    ADD COLUMN IF NOT EXISTS currency_code VARCHAR(3);

-- Backfill from the Offering the line item was sold from.
-- tenant_id is part of the join so cross-tenant offerings cannot leak currency.
UPDATE deal_line_items dli
SET currency_code = o.currency_code
FROM offerings o
WHERE dli.offering_id = o.id
  AND dli.tenant_id = o.tenant_id
  AND dli.currency_code IS NULL
  AND o.currency_code IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Verification (run after applying)
-- ---------------------------------------------------------------------------
-- Expected: 0 rows where the offering has a currency but the line item does not.
--
-- SELECT COUNT(*) AS unbackfilled
-- FROM deal_line_items dli
-- JOIN offerings o ON o.id = dli.offering_id AND o.tenant_id = dli.tenant_id
-- WHERE dli.currency_code IS NULL
--   AND o.currency_code IS NOT NULL;
--
-- Remaining NULLs belong to offerings that never had a currency configured.
-- ---------------------------------------------------------------------------