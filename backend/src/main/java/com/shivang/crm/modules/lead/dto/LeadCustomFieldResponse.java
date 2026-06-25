package com.shivang.crm.modules.lead.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
@Schema(name = "LeadCustomFieldResponse", description = "Custom field definition for leads")
public class LeadCustomFieldResponse {

    @JsonProperty("id")
    @Schema(description = "Unique identifier")
    private UUID id;

    @JsonProperty("fieldKey")
    @Schema(example = "vehicle_type")
    private String fieldKey;

    @JsonProperty("fieldLabel")
    @Schema(example = "Vehicle Type")
    private String fieldLabel;

    @JsonProperty("fieldType")
    @Schema(example = "SELECT", description = "TEXT, TEXTAREA, NUMBER, EMAIL, PHONE, DATE, BOOLEAN, SELECT, MULTISELECT, URL")
    private String fieldType;

    @JsonProperty("isRequired")
    private Boolean isRequired;

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonProperty("displayOrder")
    private Integer displayOrder;

    @JsonProperty("options")
    @Schema(description = "Options for SELECT/MULTISELECT fields")
    private List<Map<String, String>> options;

    @JsonProperty("createdAt")
    private Instant createdAt;
}
