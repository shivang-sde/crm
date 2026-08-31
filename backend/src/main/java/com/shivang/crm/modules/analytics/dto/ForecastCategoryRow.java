package com.shivang.crm.modules.analytics.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shivang.crm.modules.deal.entity.ForecastCategory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AN-15 B: one row per deal forecast category ({@code deals.forecast_category}).
 * This is a pure current-data breakout of the persisted enum - no forecasting
 * or probability math is applied. Deals created in the selected period and
 * inside the caller's resolved analytics scope, grouped by their current
 * {@link ForecastCategory}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForecastCategoryRow {

    /** The persisted forecast category (null/blank deals roll into "Unspecified"). */
    private String category;

    /** Number of deals created in the period and in scope with this category. */
    private long dealCount;

    /** SUM(amount) of those deals whose current stage recordCategory = OPEN. */
    private BigDecimal pipelineValue;

    /** SUM(amount) of those deals whose current stage recordCategory = CLOSED_WON. */
    private BigDecimal wonValue;
}
