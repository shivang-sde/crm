package com.shivang.crm.modules.records.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordFieldResponse {
    private UUID id;
    private UUID tenantId;
    private UUID recordTypeId;
    private String fieldKey;
    private String fieldLabel;
    private String fieldType;
    private Boolean isRequired;
    private Boolean isActive;
    private Integer displayOrder;
    private List<String> options;
    private String defaultValue;
    private String referenceEntityType;
    private Instant createdAt;
    private Instant updatedAt;
}
