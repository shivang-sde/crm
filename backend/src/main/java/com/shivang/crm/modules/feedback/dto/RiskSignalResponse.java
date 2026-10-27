package com.shivang.crm.modules.feedback.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L3.4-P1: one deterministic risk signal, explainable from a real source record.
 *
 * <p>Every instance is produced from a row the caller could already list
 * individually: the owning Account and the {@code sourceType}/{@code sourceId}
 * pair identify it. There is no score, no probability and no composite
 * judgement here — {@link #severity} is a fixed label on the signal kind, not a
 * computed number.
 *
 * <p>{@code accountId} is always present in JSON, but may be null for a deal or
 * entitlement that has no linked account. It is never omitted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskSignalResponse {

    /**
     * The canonical customer this signal was detected for. Always serialized;
     * null when the source deal or entitlement has no linked account.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private UUID accountId;

    /** Which deterministic rule fired. */
    private RiskSignalType type;

    /** Fixed severity for the rule. Not a score, and not comparable across types. */
    private RiskSignalSeverity severity;

    /** Human-readable explanation, safe to show a user. */
    private String reason;

    /**
     * When the underlying fact occurred — the source row's own timestamp where it
     * has one. Never {@code Instant.now()}: a signal that claims to be "now" for a
     * record written last quarter misleads whoever triages from it.
     */
    private Instant detectedAt;

    /** Domain of the source record. */
    private String sourceType;

    /** Identifier of the source record. */
    private UUID sourceId;

    /** Rule-specific detail (score, status, expiry date). Never required. */
    private Map<String, Object> metadata;

    /** The deterministic rules. */
    public enum RiskSignalType {
        NEGATIVE_NPS,
        LOW_CSAT,
        HIGH_CES,
        RECENT_COMPLAINT,
        SLA_BREACHED_TICKET,

        /**
         * L3.4-P1, extended in L3.4-P3. Coverage is still ACTIVE and either ends
         * inside the 30-day horizon, or has a recorded renewal decision date
         * ({@code renewalDueDate}, derived as {@code endDate - renewalNoticeDays})
         * inside it. The second case is the renewal window: the notice deadline
         * falls before expiry, so P1's expiry-only test would report it too late.
         *
         * <p>Still purely a coverage-lapse warning. It makes no claim about
         * renewal probability, obligation or payment.
         */
        ENTITLEMENT_APPROACHING_EXPIRY,

        /**
         * L3.4-P3. Coverage ended and nothing replaced it: an EXPIRED entitlement
         * whose end date has passed, with no ACTIVE or PENDING entitlement for the
         * same account and offering still running.
         *
         * <p>A replacement is required for suppression because renewal linkage
         * ({@code renewedFromEntitlementId}, {@code renewedToEntitlementId},
         * {@code renewalDealId}) is never written by any production path, so the
         * CRM cannot otherwise tell a renewal from a lapse.
         *
         * <p>Not churn, not payment failure and not a statement about whether the
         * customer wanted the coverage.
         */
        ENTITLEMENT_EXPIRED,

        RECENT_DEAL_LOSS,

        /**
         * L3.4-P2, engagement. Raised only for an account that has submitted
         * feedback before and has submitted none inside the window.
         *
         * <p>It asserts silence, not disengagement. The CRM holds no inbound
         * email, portal, web-analytics or product-usage trail, so nothing here
         * can claim the customer has stopped interacting with us.
         */
        ENGAGEMENT_FEEDBACK_SILENT,

        /**
         * L3.4-P2, support. A first agent reply was recorded, but only after the
         * ticket's own persisted {@code responseDueAt} had already passed.
         */
        SUPPORT_SLOW_FIRST_RESPONSE,

        /**
         * L3.4-P2, support. Still OPEN or IN_PROGRESS, and already past the
         * resolution deadline snapshotted on the ticket itself.
         */
        SUPPORT_PAST_RESOLUTION_DUE,

        /**
         * L3.4-P2, support. Still OPEN or IN_PROGRESS with no first reply at all,
         * and already past the response deadline snapshotted on the ticket itself.
         * Distinct from P1's {@link #SLA_BREACHED_TICKET}, which only appears
         * once the scheduler has stamped {@code sla_breached}.
         */
        SUPPORT_NO_RESPONSE_PAST_DUE
    }

    /**
     * Severity is assigned per rule, never computed.
     *
     * <p>Ordering is meaningful only for display; {@code #rank()} is what the
     * cross-account listing sorts on.
     */
    public enum RiskSignalSeverity {
        HIGH(0),
        MEDIUM(1);

        private final int rank;

        RiskSignalSeverity(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }
    }
}