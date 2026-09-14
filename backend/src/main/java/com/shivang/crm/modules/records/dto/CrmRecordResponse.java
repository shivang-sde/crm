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
public class CrmRecordResponse {
    private UUID id;
    private UUID tenantId;
    private UUID recordTypeId;
    private String recordTypeKey;
    private String recordTypeName;
    private Map<String, Object> data;
    private UUID createdBy;
    private UUID ownerId;
    private Instant createdAt;
    private Instant updatedAt;
}
