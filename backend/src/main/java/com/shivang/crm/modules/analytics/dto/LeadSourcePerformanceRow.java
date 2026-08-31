package com.shivang.crm.modules.analytics.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AN-16 A1: lead source performance with created-window cohort semantics.
 * One row per source for leads created in the selected period and inside the
 * caller's resolved analytics scope. Leads with no (or unnamed) source fall
 * into the stable UNSPECIFIED bucket so the summed leadCount reconciles with
 * summary.leads. convertedCount / conversionRate refer to the same created
 * cohort - never the converted-during-period event basis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeadSourcePerformanceRow {

    /** Source id owning the aggregated leads (null for the UNSPECIFIED bucket). */
    private UUID sourceId;

    /** Source name, or the stable "UNSPECIFIED" bucket for leads with no source. */
    private String source;

    /** Leads created in the period and in scope that carry this source. */
    private long leadCount;

    /** Of those same leads, the ones that have since converted (is_converted). */
    private long convertedCount;

    /** convertedCount / leadCount * 100, or 0 when leadCount == 0. */
    private double conversionRate;
}
