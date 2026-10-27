-- Terminal DEAD state for the transactional outbox.
--
-- Before this migration the publish loop retried a failing row forever:
--   OutboxEventStatus was PENDING | PROCESSING | PUBLISHED (no terminal state),
--   attempts was an unbounded counter, and no purge or alerting existed.
-- A permanently unpublishable event therefore retried at the backoff cap until the
-- table grew without bound.
--
-- dead_at records when a row exhausted app.outbox.max-attempts. The partial index
-- supports both alerting ("how many rows are DEAD?") and operator lookup. Rows in
-- this state are intentionally NOT selected by claimAvailable(), and the publisher
-- transitions are guarded by status = 'PROCESSING', so a DEAD row cannot be
-- resurrected by a late publisher or by another app instance.

ALTER TABLE crm_event_outbox
    ADD COLUMN IF NOT EXISTS dead_at TIMESTAMP;

-- Supports "alert when dead rows exist" and operator inspection. Deliberately does
-- NOT match the claim predicate in OutboxEventRepository.claimAvailable, so this
-- index cannot be used to pull terminal rows back into the publish loop.
CREATE INDEX IF NOT EXISTS idx_crm_event_outbox_dead
    ON crm_event_outbox (status, dead_at)
    WHERE deleted = false AND status = 'DEAD';
