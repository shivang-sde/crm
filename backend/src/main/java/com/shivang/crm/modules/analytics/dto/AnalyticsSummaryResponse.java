package com.shivang.crm.modules.analytics.dto;

import java.math.BigDecimal;
import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shivang.crm.modules.analytics.AnalyticsScope;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalyticsSummaryResponse {

    private AnalyticsScope scope;
    private Instant from;
    private Instant to;

    // Basic entity counts (AN-2, unchanged)

    // Basic entity counts (AN-2, unchanged)
    private long leads;
    private long contacts;
    private long deals;
    private long tasks;
    private long calls;
    private long meetings;

    // Expanded metrics (AN-3)
    private LeadMetrics leadMetrics;
    private DealMetrics dealMetrics;
    private ActivityMetrics activityMetrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeadMetrics {
        /** Leads created in range (same as leads field). */
        private long newLeads;
        /** Of newLeads, those that have since converted (isConverted=true, convertedAt IS NOT NULL). */
        private long convertedLeads;
        /** convertedLeads / newLeads * 100, or 0 when newLeads == 0. */
        private double conversionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DealMetrics {
        /** Deals in a stage with recordCategory = OPEN. */
        private long openDeals;
        /** Deals in a stage with recordCategory = CLOSED_WON. */
        private long wonDeals;
        /** Deals in a stage with recordCategory = CLOSED_LOST. */
        private long lostDeals;
        /** SUM(amount) for open deals. */
        private BigDecimal pipelineValue;
        /** SUM(amount) for won deals. */
        private BigDecimal wonValue;
        /** wonDeals / (wonDeals + lostDeals) * 100, or 0 when no closed deals. */
        private double winRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityMetrics {
        /** Tasks created in range where isClosed != true. */
        private long openTasks;
        /** Tasks created in range that were also completed within the range (status = COMPLETED, completedAt in [from,to)). */
        private long completedTasks;
        /** Tasks created in range, not completed, with dueDate before now. */
        private long overdueTasks;
    }

    // Expanded metrics (AN-3)
    private LeadMetrics leadMetrics;
    private DealMetrics dealMetrics;
    private ActivityMetrics activityMetrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeadMetrics {
        /** Leads created in range (same as leads field). */
        private long newLeads;
        /** Leads with isConverted=true and convertedAt in range. */
        private long convertedLeads;
        /** convertedLeads / newLeads * 100, or 0 when newLeads == 0. */
        private double conversionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DealMetrics {
        /** Deals in a stage with recordCategory = OPEN. */
        private long openDeals;
        /** Deals in a stage with recordCategory = CLOSED_WON. */
        private long wonDeals;
        /** Deals in a stage with recordCategory = CLOSED_LOST. */
        private long lostDeals;
        /** SUM(amount) for open deals. */
        private BigDecimal pipelineValue;
        /** SUM(amount) for won deals. */
        private BigDecimal wonValue;
        /** wonDeals / (wonDeals + lostDeals) * 100, or 0 when no closed deals. */
        private double winRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityMetrics {
        /** Tasks created in range where isClosed != true. */
        private long openTasks;
        /** Tasks completed (status = COMPLETED) in range. */
        private long completedTasks;
        /** Tasks created in range, not completed, with dueDate before now. */
        private long overdueTasks;
    }
}
