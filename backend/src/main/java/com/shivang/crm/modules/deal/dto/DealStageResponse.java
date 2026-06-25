package com.shivang.crm.modules.deal.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
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
@Schema(description = "Response payload for a deal stage")
public class DealStageResponse {

    @Schema(description = "Deal stage UUID")
    private UUID id;

    @Schema(description = "Tenant UUID")
    @JsonProperty("tenant_id")
    private UUID tenantId;

    @Schema(description = "Stage name")
    private String name;

    @Schema(description = "Stage color")
    private String color;

    @Schema(description = "Display order")
    @JsonProperty("display_order")
    private Integer displayOrder;

    @Schema(description = "Is this the default stage?")
    @JsonProperty("is_default")
    private Boolean isDefault;

    @Schema(description = "Is this a closed stage (Won/Lost)?")
    @JsonProperty("is_closed")
    private Boolean isClosed;

    @Schema(description = "Record category for deals in this stage")
    @JsonProperty("record_category")
    private RecordCategory recordCategory;

    @Schema(description = "Default probability for deals in this stage")
    @JsonProperty("default_probability")
    private Integer defaultProbability;

    @Schema(description = "Default forecast category for deals in this stage")
    @JsonProperty("default_forecast_category")
    private ForecastCategory defaultForecastCategory;

    @Schema(description = "Timestamp when stage was created")
    @JsonProperty("created_at")
    private Instant createdAt;

    @Schema(description = "Timestamp when stage was last updated")
    @JsonProperty("updated_at")
    private Instant updatedAt;
}
