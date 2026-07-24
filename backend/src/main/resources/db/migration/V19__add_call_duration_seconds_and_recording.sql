-- ============================================================================
-- Add duration_seconds and recording_url to calls table
-- SellSpark returns call_duration in seconds; storing as minutes loses
-- sub-minute precision (e.g. 4 seconds → 0 minutes).
-- recording_url was previously stored in customData JSONB; a proper column
-- enables direct mapping in CallResponse without parsing customData.
-- ============================================================================

ALTER TABLE calls
    ADD COLUMN IF NOT EXISTS duration_seconds INTEGER;

ALTER TABLE calls
    ADD COLUMN IF NOT EXISTS recording_url TEXT;
