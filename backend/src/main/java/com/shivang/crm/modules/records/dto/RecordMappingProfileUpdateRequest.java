package com.shivang.crm.modules.records.dto;

import java.util.Map;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordMappingProfileUpdateRequest {

    @Size(max = 200)
    private String name;

    @Size(max = 2000)
    private String description;

    private String mode;

    private Map<String, Object> configuration;

    private Boolean isActive;
}
