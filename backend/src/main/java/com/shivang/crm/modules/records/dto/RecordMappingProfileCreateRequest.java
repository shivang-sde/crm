package com.shivang.crm.modules.records.dto;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class RecordMappingProfileCreateRequest {

    @NotNull(message = "recordTypeId is required")
    private UUID recordTypeId;

    @NotBlank(message = "mappingKey is required")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "mappingKey must be lowercase alphanumeric with underscores, starting with a letter")
    @Size(max = 100)
    private String mappingKey;

    @NotBlank(message = "name is required")
    @Size(max = 200)
    private String name;

    @Size(max = 2000)
    private String description;

    @NotBlank(message = "mode is required")
    private String mode;

    private Map<String, Object> configuration;

    private Boolean isActive;
}
