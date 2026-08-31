package com.shivang.crm.modules.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Call status summary for calls created within the selected period and inside
 * the caller's resolved analytics scope. Counts map 1:1 to
 * {@code Call.CallStatus}: PLANNED, HELD, NOT_HELD, CANCELLED.
 *
 * heldRate is deterministic: held / (held + notHeld + cancelled) * 100
 * (deliberately excludes scheduled/planned calls) and is 0 when the
 * denominator is zero, never NaN/infinity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallStatusSummary {

    /** Calls in status PLANNED (scheduled). */
    private long planned;

    /** Calls in status HELD. */
    private long held;

    /** Calls in status NOT_HELD. */
    private long notHeld;

    /** Calls in status CANCELLED. */
    private long cancelled;

    /** held / (held + notHeld + cancelled) * 100, or 0 on a zero denominator. */
    private double heldRate;
}