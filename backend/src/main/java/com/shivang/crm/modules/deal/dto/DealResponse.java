package com.shivang.crm.modules.deal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

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
    private UUID tenantId;

    @Schema(description = "Deal name")
    private String name;

    @Schema(description = "Associated account UUID")
    private UUID accountId;

    @Schema(description = "Associated contact UUID")
    private UUID contactId;

    @Schema(description = "Deal stage with details")
    private DealStageResponse stage;

    @Schema(description = "Associated lead UUID")
    private UUID leadId;

    @Schema(description = "Deal amount")
    private BigDecimal amount;

    @Schema(description = "Expected close date")
    private LocalDate expectedCloseDate;

    @Schema(description = "Actual closed date")
    private LocalDate closedDate;

    @Schema(description = "Probability percentage (0-100)")
    private Integer probability;

    @Schema(description = "Expected revenue")
    private BigDecimal expectedRevenue;

    @Schema(description = "Forecast category")
    private ForecastCategory forecastCategory;

    @Schema(description = "Record category derived from stage")
    private RecordCategory recordCategory;

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

    @Schema(description = "Whether deal is won")
    private Boolean isWon;

    @Schema(description = "Whether deal is lost")
    private Boolean isLost;

    @Schema(description = "Custom fields")
    private Map<String, Object> customData;

    @Schema(description = "User who created this deal")
    private UUID createdBy;

    @Schema(description = "User who last updated this deal")
    private UUID updatedBy;

    @Schema(description = "Timestamp when deal was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when deal was last updated")
    private Instant updatedAt;
}
