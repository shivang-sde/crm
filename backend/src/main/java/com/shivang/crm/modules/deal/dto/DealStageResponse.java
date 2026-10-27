package com.shivang.crm.modules.deal.dto;

import java.time.Instant;
import java.util.UUID;

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
    private UUID tenantId;

    @Schema(description = "Stage name")
    private String name;

    @Schema(description = "Stage color")
    private String color;

    @Schema(description = "Display order")
    private Integer displayOrder;

    @Schema(description = "Is this the default stage?")
    private Boolean isDefault;

    @Schema(description = "Is this a closed stage (Won/Lost)?")
    private Boolean isClosed;

    @Schema(description = "Record category for deals in this stage")
    private RecordCategory recordCategory;

    @Schema(description = "Default probability for deals in this stage")
    private Integer defaultProbability;

    @Schema(description = "Default forecast category for deals in this stage")
    private ForecastCategory defaultForecastCategory;

    @Schema(description = "Timestamp when stage was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when stage was last updated")
    private Instant updatedAt;
}
