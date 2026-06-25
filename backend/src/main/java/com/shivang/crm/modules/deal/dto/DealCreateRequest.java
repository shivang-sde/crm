package com.shivang.crm.modules.deal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.deal.entity.DealType;
import com.shivang.crm.modules.deal.entity.ForecastCategory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Request payload for creating a new deal")
public class DealCreateRequest {

    @NotBlank(message = "Deal name is required")
    @Schema(description = "Deal name", example = "Enterprise Solution Deal")
    private String name;

    @Schema(description = "Deal stage UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    @JsonProperty("stage_id")
    private UUID stageId;

    @Schema(description = "Associated account UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    @JsonProperty("account_id")
    private UUID accountId;

    @Schema(description = "Associated contact UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    @JsonProperty("contact_id")
    private UUID contactId;

    @Schema(description = "Associated lead UUID (for lead-to-deal conversion)", example = "550e8400-e29b-41d4-a716-446655440000")
    @JsonProperty("lead_id")
    private UUID leadId;

    @Schema(description = "Deal amount", example = "150000.00")
    private BigDecimal amount;

    @Schema(description = "Expected close date", example = "2026-12-31")
    @JsonProperty("expected_close_date")
    private LocalDate expectedCloseDate;

    @Schema(description = "Actual closed date")
    @JsonProperty("closed_date")
    private LocalDate closedDate;

    @Schema(description = "Probability percentage (0-100)", example = "75")
    private Integer probability;

    @Schema(description = "Forecast category")
    @JsonProperty("forecast_category")
    private ForecastCategory forecastCategory;

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

    @Schema(description = "Deal owner user UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    @JsonProperty("owner_user_id")
    private UUID ownerUserId;

    @Schema(description = "Custom fields as key-value map")
    @JsonProperty("custom_data")
    private Map<String, Object> customData;
}
