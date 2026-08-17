package com.shivang.crm.modules.acquisition.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.acquisition.mapping.LeadIngestionTargetType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LeadIngestionTargetField", description = "Allowed target field for ingestion mapping")
public class LeadIngestionTargetField {

    @JsonProperty("targetType")
    private LeadIngestionTargetType targetType;

    @JsonProperty("fieldKey")
    private String fieldKey;

    @JsonProperty("label")
    private String label;

    @JsonProperty("dataType")
    private String dataType;

    @JsonProperty("required")
    private Boolean required;
}
