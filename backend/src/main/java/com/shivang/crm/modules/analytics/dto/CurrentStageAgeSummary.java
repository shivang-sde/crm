package com.shivang.crm.modules.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AN-15 C: aging/average-age summary for OPEN deals created in the selected
 * period and inside the caller's resolved analytics scope.
 *
 *   avgDealAgeDays               = AVG(now - created_at), open deals, days
 *   avgCurrentStageAgeDays       = AVG(now - stage_entered_at), only open deals
 *                                 that have a non-null stage_entered_at, days
 *   openDealsWithStageEnteredAt  = count of open deals with stage_entered_at
 *   openDealsWithoutStageEnteredAt = count of open deals with NULL stage_entered_at
 *
 * "Time in current stage" is explicitly a current snapshot (now - stage_entered_at),
 * not a historical time-in-stage. Deals without stage_entered_at are counted
 * separately and excluded from the average; they are never fabricated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CurrentStageAgeSummary {

    /** Average age of open deals (created_at basis), in days, or 0 when none. */
    private double avgDealAgeDays;

    /** Average time in current stage (stage_entered_at basis), in days, or 0 when none. */
    private double avgCurrentStageAgeDays;

    /** Open deals created in the period that have a non-null stage_entered_at. */
    private long openDealsWithStageEnteredAt;

    /** Open deals created in the period whose stage_entered_at is null. */
    private long openDealsWithoutStageEnteredAt;
}
