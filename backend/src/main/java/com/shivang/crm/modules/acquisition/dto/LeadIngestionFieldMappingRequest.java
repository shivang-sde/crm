package com.shivang.crm.modules.acquisition.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.acquisition.mapping.LeadIngestionTargetType;
import com.shivang.crm.modules.acquisition.mapping.LeadIngestionTransformType;

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
@Schema(name = "LeadIngestionFieldMappingRequest", description = "Request payload for creating/updating ingestion field mapping")
public class LeadIngestionFieldMappingRequest {

    @NotBlank(message = "Source path is required")
    @Size(max = 500, message = "Source path cannot exceed 500 characters")
    @JsonProperty("sourcePath")
    private String sourcePath;

    @NotNull(message = "Target type is required")
    @JsonProperty("targetType")
    private LeadIngestionTargetType targetType;

    @NotBlank(message = "Target field is required")
    @Size(max = 100, message = "Target field cannot exceed 100 characters")
    @JsonProperty("targetField")
    private String targetField;

    @JsonProperty("transformType")
    private LeadIngestionTransformType transformType;

    @JsonProperty("transformConfig")
    private Map<String, Object> transformConfig;

    @JsonProperty("defaultValue")
    private String defaultValue;

    @JsonProperty("required")
    private Boolean required;

    @JsonProperty("active")
    private Boolean active;

    @JsonProperty("displayOrder")
    private Integer displayOrder;
}
