package com.shivang.crm.modules.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AN-15 D: call-duration metrics for calls created in the selected period and
 * inside the caller's resolved analytics scope. Uses ONLY the authoritative
 * {@code durationMinutes} captured by AN-14 (manual HELD completion) or CDR.
 *
 * A null/absent duration is never interpreted as zero: calls without captured
 * duration are reported separately so users can see coverage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallDurationSummary {

    /** Total calls created in the period and in scope. */
    private long callsTotal;

    /** Calls that have a captured (non-null) durationMinutes. */
    private long callsWithDuration;

    /** callsTotal - callsWithDuration (reported, not assumed zero). */
    private long callsWithoutDuration;

    /** SUM(durationMinutes) over calls with captured duration. */
    private long totalCallMinutes;

    /** totalCallMinutes / callsWithDuration, or 0 when callsWithDuration == 0. */
    private double averageCallDurationMinutes;
}
