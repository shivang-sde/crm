package com.shivang.crm.modules.acquisition.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.acquisition.config.LeadIngestionTransportType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LeadIngestionConfigCreateRequest", description = "Request to create a lead ingestion configuration")
public class LeadIngestionConfigCreateRequest {

    @NotBlank(message = "Configuration name is required")
    @Size(max = 200, message = "Configuration name cannot exceed 200 characters")
    @JsonProperty("name")
    @Schema(example = "Website Leads", description = "Human-readable ingestion configuration name")
    private String name;

    @NotNull(message = "Transport type is required")
    @JsonProperty("transportType")
    @Schema(example = "WEBHOOK", description = "How data enters the ingestion engine")
    private LeadIngestionTransportType transportType;

    @JsonProperty("active")
    @Schema(example = "true", description = "Whether this ingestion configuration is active")
    private Boolean active;

    @JsonProperty("settings")
    @Schema(description = "Transport-specific settings blob")
    private Map<String, Object> settings;
}