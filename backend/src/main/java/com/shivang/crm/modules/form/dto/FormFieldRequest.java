package com.shivang.crm.modules.form.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormFieldRequest {

    private String id; // for existing fields, null for new

    @NotBlank(message = "Field key is required")
    @Size(max = 100, message = "Field key cannot exceed 100 characters")
    private String fieldKey;

    @NotBlank(message = "Field type is required")
    private String type;

    @NotBlank(message = "Label is required")
    @Size(max = 200, message = "Label cannot exceed 200 characters")
    private String label;

    @Size(max = 200, message = "Placeholder too long")
    private String placeholder;

    @Size(max = 1000, message = "Help text too long")
    private String helpText;

    private Boolean required;

    private Integer orderIndex;

    private String defaultValue;

    private List<Map<String, String>> options;

    private String crmTargetType;

    private String crmTargetField;

    private String transformType;

    private Map<String, Object> transformConfig;
}
