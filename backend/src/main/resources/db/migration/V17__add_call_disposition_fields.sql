-- Add disposition and notes fields to calls for Phase 9D call outcome capture
ALTER TABLE calls
    ADD COLUMN IF NOT EXISTS disposition VARCHAR(100),
    ADD COLUMN IF NOT EXISTS notes TEXT,
    ADD COLUMN IF NOT EXISTS next_action VARCHAR(100),
    ADD COLUMN IF NOT EXISTS follow_up_at TIMESTAMP;
