package com.shivang.crm.modules.deal.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.deal.entity.ForecastCategory;
import com.shivang.crm.modules.deal.entity.RecordCategory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesDashboardResponse {

    private DealSummary deals;

    @JsonProperty("stage_breakdown")
    private List<StageBreakdown> stages;

    @JsonProperty("owner_breakdown")
    private List<OwnerBreakdown> owners;

    @JsonProperty("lead_funnel")
    private LeadFunnel leadFunnel;

    private ClosingMetrics closing;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DealSummary {
        private long totalCount;
        private long openCount;
        private long wonCount;
        private long lostCount;

        @JsonProperty("open_pipeline_value")
        private BigDecimal openPipelineValue;

        @JsonProperty("weighted_pipeline_value")
        private BigDecimal weightedPipelineValue;

        @JsonProperty("won_value")
        private BigDecimal wonValue;

        @JsonProperty("lost_value")
        private BigDecimal lostValue;

        @JsonProperty("average_open_deal_size")
        private BigDecimal averageOpenDealSize;

        @JsonProperty("average_days_in_pipeline")
        private BigDecimal averageDaysInPipeline;

        @JsonProperty("max_open_deal_age_days")
        private Long maxOpenDealAgeDays;

        @JsonProperty("average_days_in_current_stage")
        private BigDecimal averageDaysInCurrentStage;

        @JsonProperty("stale_deal_count")
        private long staleDealCount;

        @JsonProperty("stale_deal_value")
        private BigDecimal staleDealValue;

        @JsonProperty("stale_deal_weighted_value")
        private BigDecimal staleDealWeightedValue;

        @JsonProperty("stale_deal_percentage")
        private BigDecimal staleDealPercentage;

        @JsonProperty("forecast_by_category")
        private Map<ForecastCategory, BigDecimal> forecastByCategory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageBreakdown {
        private UUID stageId;
        private String stageName;
        private String color;
        private Integer displayOrder;
        private RecordCategory recordCategory;
        private long count;

        @JsonProperty("total_amount")
        private BigDecimal totalAmount;

        @JsonProperty("average_days_in_stage")
        private BigDecimal averageDaysInStage;

        @JsonProperty("stale_count")
        private long staleCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OwnerBreakdown {
        @JsonProperty("owner_user_id")
        private UUID ownerUserId;

        @JsonProperty("owner_name")
        private String ownerName;

        @JsonProperty("open_count")
        private long openCount;

        @JsonProperty("won_count")
        private long wonCount;

        @JsonProperty("lost_count")
        private long lostCount;

        @JsonProperty("open_value")
        private BigDecimal openValue;

        @JsonProperty("won_value")
        private BigDecimal wonValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeadFunnel {

        @JsonProperty("total_leads")
        private long totalLeads;

        @JsonProperty("open_leads")
        private long openLeads;

        @JsonProperty("converted_leads")
        private long convertedLeads;

        @JsonProperty("conversion_rate_percent")
        private double conversionRatePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClosingMetrics {

        @JsonProperty("expected_close_next_30_days_count")
        private long expectedCloseNext30DaysCount;

        @JsonProperty("expected_close_next_30_days_value")
        private BigDecimal expectedCloseNext30DaysValue;

        @JsonProperty("overdue_expected_close_count")
        private long overdueExpectedCloseCount;

        @JsonProperty("overdue_expected_close_value")
        private BigDecimal overdueExpectedCloseValue;

        @JsonProperty("average_sales_cycle_days")
        private Long averageSalesCycleDays;

        @JsonProperty("won_value_last_30_days")
        private BigDecimal wonValueLast30Days;
    }
}
