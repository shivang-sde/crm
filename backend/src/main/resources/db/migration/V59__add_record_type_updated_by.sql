-- WF-58 fix: record_types missing updated_by column (entity expects it)
ALTER TABLE record_types ADD COLUMN IF NOT EXISTS updated_by UUID;
