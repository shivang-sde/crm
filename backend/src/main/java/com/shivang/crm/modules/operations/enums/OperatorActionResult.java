package com.shivang.crm.modules.operations.enums;

/**
 * Outcome of an operator recovery attempt.
 *
 * <p>Only {@link #SUCCESS} participates in the duplicate-prevention unique index.
 * {@link #FAILED} is recorded so the attempt is auditable without blocking a
 * later legitimate attempt.
 */
public enum OperatorActionResult {
    SUCCESS,
    FAILED,
    /** A previous successful action already exists for this target. */
    CONFLICT,
    NOT_FOUND,
    /** Rejected before any side effect: wrong scope, or an unapproved destination. */
    NOT_ALLOWED
}
