package com.shivang.crm.modules.form.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormFieldResponse {

    private UUID id;
    private String fieldKey;
    private String type;
    private String label;
    private String placeholder;
    private String helpText;
    private Boolean required;
    private Integer orderIndex;
    private String defaultValue;
    private List<Map<String, String>> options;
    private String crmTargetType;
    private String crmTargetField;
    private String transformType;
    private Map<String, Object> transformConfig;
    private Instant createdAt;
    private Instant updatedAt;
}
