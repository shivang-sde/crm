package com.shivang.crm.modules.feedback.entity;

import java.time.Instant;

/**
 * L3.5: lifecycle of a ticket feedback request.
 *
 * <p>Not persisted. The timestamps that produce it are, and this ranking is the
 * only place the precedence lives: a submitted response is final even if it
 * arrived a moment before expiry or revocation, and an explicit revocation
 * outranks expiry so staff can tell "we cancelled this" from "it timed out".
 */
public enum FeedbackRequestStatus {
    PENDING,
    SUBMITTED,
    EXPIRED,
    REVOKED;

    public static FeedbackRequestStatus at(
            Instant submittedAt, Instant revokedAt, Instant expiresAt, Instant now) {
        if (submittedAt != null) {
            return SUBMITTED;
        }
        if (revokedAt != null) {
            return REVOKED;
        }
        if (!expiresAt.isAfter(now)) {
            return EXPIRED;
        }
        return PENDING;
    }
}
