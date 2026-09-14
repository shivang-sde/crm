-- ============================================================================
-- WF-49: Webhook Security Foundation
-- Add authentication mode and secret storage to record_webhooks
-- ============================================================================

ALTER TABLE record_webhooks
    ADD COLUMN IF NOT EXISTS auth_mode VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS secret_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS secret_encrypted TEXT;

-- Backfill existing rows to NONE (already default)
UPDATE record_webhooks SET auth_mode = 'NONE' WHERE auth_mode IS NULL;

-- Constraint for auth_mode values
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_webhook_auth_mode') THEN
        ALTER TABLE record_webhooks ADD CONSTRAINT chk_webhook_auth_mode CHECK (auth_mode IN ('NONE','API_KEY','HMAC_SHA256'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_record_webhooks_auth_mode ON record_webhooks(auth_mode);
