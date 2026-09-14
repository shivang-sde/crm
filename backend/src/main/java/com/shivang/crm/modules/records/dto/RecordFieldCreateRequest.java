package com.shivang.crm.modules.records.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordFieldCreateRequest {

    @NotBlank(message = "Field key is required")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "Field key must be lowercase alphanumeric with underscores, starting with a letter")
    @Size(max = 100, message = "Field key must be at most 100 characters")
    private String fieldKey;

    @NotBlank(message = "Field label is required")
    @Size(max = 200, message = "Label must be at most 200 characters")
    private String fieldLabel;

    @NotBlank(message = "Field type is required")
    private String fieldType;

    private Boolean isRequired;
    private Boolean isActive;
    private Integer displayOrder;
    private List<String> options;
    private String defaultValue;
    private String referenceEntityType;
}
