package com.shivang.crm.modules.lead.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LeadStatusResponse", description = "Lead status details")
public class LeadStatusResponse {

    @JsonProperty("id")
    @Schema(description = "Unique identifier")
    private UUID id;

    @JsonProperty("name")
    @Schema(example = "New")
    private String name;

    @JsonProperty("color")
    @Schema(example = "#FF5733")
    private String color;

    @JsonProperty("displayOrder")
    private Integer displayOrder;

    @JsonProperty("isDefault")
    private Boolean isDefault;

    @JsonProperty("isClosed")
    private Boolean isClosed;

    @JsonProperty("createdAt")
    private Instant createdAt;
}
