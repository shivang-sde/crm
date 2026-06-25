package com.shivang.crm.modules.lead.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LeadStatusCreateRequest", description = "Request to create a lead status")
public class LeadStatusCreateRequest {

    @NotBlank(message = "Status name is required")
    @JsonProperty("name")
    @Schema(example = "New", description = "Name of the status")
    private String name;

    @JsonProperty("color")
    @Schema(example = "#FF5733", description = "Color code for the status")
    private String color;

    @JsonProperty("displayOrder")
    @Schema(example = "1", description = "Display order")
    private Integer displayOrder;

    @JsonProperty("isDefault")
    @Schema(example = "false", description = "Mark as default status")
    private Boolean isDefault;

    @JsonProperty("isClosed")
    @Schema(example = "false", description = "Mark as closed status")
    private Boolean isClosed;
}
