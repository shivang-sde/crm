package com.shivang.crm.modules.acquisition.dto;

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
@Schema(name = "LeadIngestionSourceField", description = "Discovered source field from captured payload")
public class LeadIngestionSourceField {

    @JsonProperty("path")
    private String path;

    @JsonProperty("sampleValue")
    private Object sampleValue;

    @JsonProperty("detectedType")
    private LeadIngestionSourceFieldType detectedType;
}
