package com.shivang.crm.modules.records.dto;

import java.time.Instant;
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
public class RecordMappingProfileResponse {
    private UUID id;
    private UUID tenantId;
    private UUID recordTypeId;
    private String recordTypeKey;
    private String recordTypeName;
    private String mappingKey;
    private String name;
    private String description;
    private String mode;
    private Map<String, Object> configuration;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
