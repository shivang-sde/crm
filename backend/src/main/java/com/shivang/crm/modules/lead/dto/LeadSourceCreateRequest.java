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
@Schema(name = "LeadSourceCreateRequest", description = "Request to create a lead source")
public class LeadSourceCreateRequest {

    @NotBlank(message = "Source name is required")
    @JsonProperty("name")
    @Schema(example = "Website", description = "Name of the source")
    private String name;

    @JsonProperty("isActive")
    @Schema(example = "true", description = "Whether this source is active")
    private Boolean isActive;
}
