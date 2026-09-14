package com.shivang.crm.modules.records.dto;

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
public class RecordTypeCreateRequest {

    @NotBlank(message = "Key is required")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "Key must be lowercase alphanumeric with underscores, starting with a letter")
    @Size(max = 100, message = "Key must be at most 100 characters")
    private String key;

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must be at most 200 characters")
    private String name;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    private Boolean isActive;
}
