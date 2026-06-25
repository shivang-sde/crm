package com.shivang.crm.modules.contact.dto;

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
@Schema(name = "ContactCustomFieldResponse", description = "Custom field definition for contacts")
public class ContactCustomFieldResponse {

    @JsonProperty("id")
    @Schema(description = "Unique identifier")
    private UUID id;

    @JsonProperty("fieldKey")
    @Schema(example = "job_title")
    private String fieldKey;

    @JsonProperty("fieldLabel")
    @Schema(example = "Job Title")
    private String fieldLabel;

    @JsonProperty("fieldType")
    @Schema(example = "TEXT", description = "TEXT, NUMBER, DATE, SELECT, MULTISELECT, etc.")
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
