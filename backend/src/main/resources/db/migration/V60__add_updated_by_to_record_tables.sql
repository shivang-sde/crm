-- Fix missing updated_by columns for record entities (entity expects it but V51/V54 did not create)
ALTER TABLE record_webhooks ADD COLUMN IF NOT EXISTS updated_by UUID;
ALTER TABLE record_webhook_deliveries ADD COLUMN IF NOT EXISTS updated_by UUID;
ALTER TABLE record_fields ADD COLUMN IF NOT EXISTS updated_by UUID;
-- record_types already covered by V59, but ensure idempotent
ALTER TABLE record_types ADD COLUMN IF NOT EXISTS updated_by UUID;
