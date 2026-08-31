package com.shivang.crm.modules.analytics.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row per open-deal age bucket for deals created within the selected
 * period. Age basis: now() - deals.created_at measured in days at query time
 * (no historical aging). Buckets: 0-7, 8-30, 31-60, 61-90, 90+ days.
 * "Open" uses the same stage.recordCategory = OPEN semantics as the summary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DealAgingRow {

    /** Bucket label (0-7, 8-30, 31-60, 61-90, 90+). */
    private String bucket;

    /** Number of open deals created in the period falling in this bucket. */
    private long count;

    /** SUM(amount) of those open deals. */
    private BigDecimal pipelineValue;
}