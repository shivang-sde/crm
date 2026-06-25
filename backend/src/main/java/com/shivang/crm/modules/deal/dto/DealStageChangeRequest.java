package com.shivang.crm.modules.deal.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Request payload for changing a deal stage")
public class DealStageChangeRequest {

    @NotNull(message = "Stage ID is required")
    @Schema(description = "Deal stage UUID")
    @JsonProperty("stage_id")
    private UUID stageId;

    @Schema(description = "Actual closed date")
    @JsonProperty("closed_date")
    private LocalDate closedDate;

    @Schema(description = "Reason why the deal was won")
    @JsonProperty("won_reason")
    private String wonReason;

    @Schema(description = "Reason why the deal was lost")
    @JsonProperty("lost_reason")
    private String lostReason;
}
