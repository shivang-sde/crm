package com.shivang.crm.modules.lead.dto;

import java.util.List;
import java.util.Map;

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
@Schema(name = "LeadCustomFieldCreateRequest", description = "Request to create a custom field")
public class LeadCustomFieldCreateRequest {

    @NotBlank(message = "Field key is required")
    @JsonProperty("fieldKey")
    @Schema(example = "vehicle_type", description = "Unique field key")
    private String fieldKey;

    @NotBlank(message = "Field label is required")
    @JsonProperty("fieldLabel")
    @Schema(example = "Vehicle Type", description = "Display label")
    private String fieldLabel;

    @NotBlank(message = "Field type is required")
    @JsonProperty("fieldType")
    @Schema(example = "SELECT", description = "TEXT, NUMBER, DATE, SELECT, MULTISELECT, etc.")
    private String fieldType;

    @JsonProperty("isRequired")
    @Schema(example = "false", description = "Is this field required")
    private Boolean isRequired;

    @JsonProperty("isActive")
    @Schema(example = "true", description = "Is this field active")
    private Boolean isActive;

    @JsonProperty("displayOrder")
    @Schema(example = "1", description = "Display order")
    private Integer displayOrder;

    @JsonProperty("options")
    @Schema(description = "Options for SELECT/MULTISELECT: [{\"label\":\"Option 1\",\"value\":\"opt_1\"}]")
    private List<Map<String, String>> options;
}
