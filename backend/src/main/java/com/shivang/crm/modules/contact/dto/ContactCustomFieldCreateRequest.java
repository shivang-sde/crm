package com.shivang.crm.modules.contact.dto;

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
@Schema(name = "ContactCustomFieldCreateRequest", description = "Request to create a custom field for contacts")
public class ContactCustomFieldCreateRequest {

    @NotBlank(message = "Field key is required")
    @JsonProperty("fieldKey")
    @Schema(example = "job_title", description = "Unique field key")
    private String fieldKey;

    @NotBlank(message = "Field label is required")
    @JsonProperty("fieldLabel")
    @Schema(example = "Job Title", description = "Display label")
    private String fieldLabel;

    @NotBlank(message = "Field type is required")
    @JsonProperty("fieldType")
    @Schema(example = "TEXT", description = "Field type such as TEXT, NUMBER, DATE, SELECT")
    private String fieldType;

    @JsonProperty("isRequired")
    private Boolean isRequired;

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonProperty("displayOrder")
    private Integer displayOrder;

    @JsonProperty("options")
    @Schema(description = "Options for select fields")
    private List<Map<String, String>> options;
}
