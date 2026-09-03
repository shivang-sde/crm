package com.shivang.crm.modules.form.dto;

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
public class FormCreateRequest {

    @NotBlank(message = "Form name is required")
    @Size(max = 200, message = "Form name cannot exceed 200 characters")
    private String name;

    @Size(max = 2000, message = "Description too long")
    private String description;
}
