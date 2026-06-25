package com.shivang.crm.modules.deal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.deal.entity.DealType;
import com.shivang.crm.modules.deal.entity.ForecastCategory;
import com.shivang.crm.modules.deal.entity.RecordCategory;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response payload for a deal")
public class DealResponse {

    @Schema(description = "Deal UUID")
    private UUID id;

    @Schema(description = "Tenant UUID")
    @JsonProperty("tenant_id")
    private UUID tenantId;

    @Schema(description = "Deal name")
    private String name;

    @Schema(description = "Associated account UUID")
    @JsonProperty("account_id")
    private UUID accountId;

    @Schema(description = "Associated contact UUID")
    @JsonProperty("contact_id")
    private UUID contactId;

    @Schema(description = "Deal stage with details")
    private DealStageResponse stage;

    @Schema(description = "Associated lead UUID")
    @JsonProperty("lead_id")
    private UUID leadId;

    @Schema(description = "Deal amount")
    private BigDecimal amount;

    @Schema(description = "Expected close date")
    @JsonProperty("expected_close_date")
    private LocalDate expectedCloseDate;

    @Schema(description = "Actual closed date")
    @JsonProperty("closed_date")
    private LocalDate closedDate;

    @Schema(description = "Probability percentage (0-100)")
    private Integer probability;

    @Schema(description = "Expected revenue")
    @JsonProperty("expected_revenue")
    private BigDecimal expectedRevenue;

    @Schema(description = "Forecast category")
    @JsonProperty("forecast_category")
    private ForecastCategory forecastCategory;

    @Schema(description = "Record category derived from stage")
    @JsonProperty("record_category")
    private RecordCategory recordCategory;

    @Schema(description = "Next step")
    @JsonProperty("next_step")
    private String nextStep;

    @Schema(description = "Deal type")
    @JsonProperty("deal_type")
    private DealType dealType;

    @Schema(description = "Lead source")
    @JsonProperty("lead_source")
    private String leadSource;

    @Schema(description = "Campaign source")
    @JsonProperty("campaign_source")
    private String campaignSource;

    @Schema(description = "Reason why the deal was won")
    @JsonProperty("won_reason")
    private String wonReason;

    @Schema(description = "Reason why the deal was lost")
    @JsonProperty("lost_reason")
    private String lostReason;

    @Schema(description = "Deal description")
    private String description;

    @Schema(description = "Deal owner user UUID")
    @JsonProperty("owner_user_id")
    private UUID ownerUserId;

    @Schema(description = "Whether deal is won")
    @JsonProperty("is_won")
    private Boolean isWon;

    @Schema(description = "Whether deal is lost")
    @JsonProperty("is_lost")
    private Boolean isLost;

    @Schema(description = "Custom fields")
    @JsonProperty("custom_data")
    private Map<String, Object> customData;

    @Schema(description = "User who created this deal")
    @JsonProperty("created_by")
    private UUID createdBy;

    @Schema(description = "User who last updated this deal")
    @JsonProperty("updated_by")
    private UUID updatedBy;

    @Schema(description = "Timestamp when deal was created")
    @JsonProperty("created_at")
    private Instant createdAt;

    @Schema(description = "Timestamp when deal was last updated")
    @JsonProperty("updated_at")
    private Instant updatedAt;
}
