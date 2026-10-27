-- ============================================================================
-- L3.5: durable, ticket-scoped customer feedback requests
-- ============================================================================
-- A staff member asks one customer (one ticket) for a score and receives a
-- single-use public link. The raw link token is never stored: only its SHA-256
-- hash is. This table is the request lifecycle; submitted answers remain in the
-- immutable feedback table created by V74.
--
-- Why status is not a column
-- --------------------------
-- PENDING / EXPIRED / SUBMITTED / REVOKED are all derivable from expires_at,
-- submitted_at and revoked_at. Storing a status would create a second truth
-- that needs a scheduled job to keep expired rows honest, and a stale PENDING
-- is exactly the state that would let a revoked link look live. The service
-- derives status at read time from the same clock used for expiry.
--
-- Why no unique pending index
-- ---------------------------
-- "At most one PENDING request per ticket" is enforced by a pessimistic lock on
-- the ticket row in FeedbackRequestService. A partial unique index cannot
-- express it because expiry is time-based, not a stored flag.
--
-- Tenant safety
-- -------------
-- Foreign keys are tenant-blind, matching the rest of the schema. Every read is
-- tenant-scoped; public token lookups are deliberately tenant-blind but only
-- ever return the safe public DTO.

CREATE TABLE IF NOT EXISTS feedback_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id       UUID NOT NULL,
    ticket_id       UUID NOT NULL,
    account_id      UUID NOT NULL,
    contact_id      UUID,

    type            VARCHAR(30) NOT NULL,

    -- SHA-256 hex of the single-use public token. The raw token exists only in
    -- the create/regenerate response and must never be logged or stored.
    token_hash      VARCHAR(64) NOT NULL,

    expires_at      TIMESTAMP NOT NULL,
    submitted_at    TIMESTAMP,
    feedback_id     UUID,
    revoked_at      TIMESTAMP,

    owner_user_id   UUID,
    created_by      UUID NOT NULL,
    updated_by      UUID,

    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),

    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP,
    deleted_by      UUID,

    CONSTRAINT fk_feedback_requests_tenant_id   FOREIGN KEY (tenant_id)   REFERENCES tenants(id),
    CONSTRAINT fk_feedback_requests_ticket_id   FOREIGN KEY (ticket_id)   REFERENCES tickets(id),
    CONSTRAINT fk_feedback_requests_account_id  FOREIGN KEY (account_id)  REFERENCES accounts(id),
    CONSTRAINT fk_feedback_requests_contact_id  FOREIGN KEY (contact_id)  REFERENCES contacts(id),
    CONSTRAINT fk_feedback_requests_feedback_id FOREIGN KEY (feedback_id) REFERENCES feedback(id),
    CONSTRAINT fk_feedback_requests_owner_id    FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT fk_feedback_requests_created_by  FOREIGN KEY (created_by)  REFERENCES users(id),
    CONSTRAINT fk_feedback_requests_updated_by  FOREIGN KEY (updated_by)  REFERENCES users(id),
    CONSTRAINT fk_feedback_requests_deleted_by  FOREIGN KEY (deleted_by)  REFERENCES users(id)
);

-- Token lookup is the public hot path. Globally unique because a token must
-- never resolve to two tenants; soft-deleted rows are never looked up.
CREATE UNIQUE INDEX IF NOT EXISTS uq_feedback_requests_token_hash
    ON feedback_requests (token_hash);

-- Ticket workflow panel: newest first for one tenant/ticket.
CREATE INDEX IF NOT EXISTS idx_feedback_requests_tenant_ticket_created
    ON feedback_requests (tenant_id, ticket_id, created_at DESC);

-- Pending-existence check during create/regenerate.
CREATE INDEX IF NOT EXISTS idx_feedback_requests_pending
    ON feedback_requests (tenant_id, ticket_id, expires_at)
    WHERE submitted_at IS NULL
      AND revoked_at IS NULL
      AND deleted = false;
