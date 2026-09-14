package com.shivang.crm.modules.records.dto;

import java.util.List;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordFieldUpdateRequest {

    @Size(max = 200, message = "Label must be at most 200 characters")
    private String fieldLabel;

    private String fieldType;
    private Boolean isRequired;
    private Boolean isActive;
    private Integer displayOrder;
    private List<String> options;
    private String defaultValue;
    private String referenceEntityType;
}
