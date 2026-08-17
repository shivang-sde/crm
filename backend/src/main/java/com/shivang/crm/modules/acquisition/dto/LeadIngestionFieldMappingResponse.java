package com.shivang.crm.modules.acquisition.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.acquisition.mapping.LeadIngestionTargetType;
import com.shivang.crm.modules.acquisition.mapping.LeadIngestionTransformType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LeadIngestionFieldMappingResponse", description = "Lead ingestion field mapping response")
public class LeadIngestionFieldMappingResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("sourcePath")
    private String sourcePath;

    @JsonProperty("targetType")
    private LeadIngestionTargetType targetType;

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

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;
}
