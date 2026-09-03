package com.shivang.crm.modules.form.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormUpdateRequest {

    @Size(max = 200, message = "Form name cannot exceed 200 characters")
    private String name;

    @Size(max = 2000, message = "Description too long")
    private String description;

    private Map<String, Object> settings;

    @Valid
    private List<FormFieldRequest> fields;
}
