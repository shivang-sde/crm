package com.shivang.crm.modules.deal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.shivang.crm.modules.deal.entity.DealType;
import com.shivang.crm.modules.deal.entity.ForecastCategory;

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
@Schema(description = "Request payload for updating a deal")
public class DealUpdateRequest {

    @Schema(description = "Deal name")
    private String name;

    @Schema(description = "Deal stage UUID")
    private UUID stageId;

    @Schema(description = "Associated account UUID")
    private UUID accountId;

    @Schema(description = "Associated contact UUID")
    private UUID contactId;

    @Schema(description = "Deal amount")
    private BigDecimal amount;

    @Schema(description = "Expected close date")
    private LocalDate expectedCloseDate;

    @Schema(description = "Actual closed date")
    private LocalDate closedDate;

    @Schema(description = "Probability percentage (0-100)")
    private Integer probability;

    @Schema(description = "Forecast category")
    private ForecastCategory forecastCategory;

    @Schema(description = "Next step")
    private String nextStep;

    @Schema(description = "Deal type")
    private DealType dealType;

    @Schema(description = "Lead source")
    private String leadSource;

    @Schema(description = "Campaign source")
    private String campaignSource;

    @Schema(description = "Reason why the deal was won")
    private String wonReason;

    @Schema(description = "Reason why the deal was lost")
    private String lostReason;

    @Schema(description = "Deal description")
    private String description;

    @Schema(description = "Deal owner user UUID")
    private UUID ownerUserId;

    @Schema(description = "Custom fields as key-value map")
    private Map<String, Object> customData;
}
