package com.shivang.crm.modules.analytics.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One aggregate row per current deal stage for deals created within the
 * selected period. Current-stage snapshot semantics (same as the summary):
 * open/won/lost reflect {@code stage.recordCategory} at query time, never
 * historical stage positions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineStageRow {

    /** Id of the deal stage the row aggregates. */
    private UUID stageId;

    /** Name of the deal stage. */
    private String stageName;

    /** Deals in this stage whose recordCategory = OPEN. */
    private long openCount;

    /** Deals in this stage whose recordCategory = CLOSED_WON. */
    private long wonCount;

    /** Deals in this stage whose recordCategory = CLOSED_LOST. */
    private long lostCount;

    /** SUM(amount) of the open deals in this stage. */
    private BigDecimal pipelineValue;

    /** SUM(amount) of the won deals in this stage. */
    private BigDecimal wonValue;

    /** Total deals in this stage (open + won + lost + other categories). */
    private long totalCount;
}