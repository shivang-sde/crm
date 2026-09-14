package com.shivang.crm.modules.records.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordWebhookResponse {
    private UUID id;
    private UUID tenantId;
    private String name;
    private String webhookKey;
    private String description;
    private UUID recordTypeId;
    private String recordTypeKey;
    private String recordTypeName;
    private UUID mappingProfileId;
    private String mappingKey;
    private String mappingName;
    private String mappingMode;
    private Boolean isActive;
    private String authMode;
    private String endpointPath;
    private Instant createdAt;
    private Instant updatedAt;
}
