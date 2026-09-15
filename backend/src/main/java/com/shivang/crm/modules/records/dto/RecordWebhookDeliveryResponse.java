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
public class RecordWebhookDeliveryResponse {
    private UUID id;
    private UUID tenantId;
    private UUID webhookId;
    private String webhookKey;
    private String webhookName;
    private UUID recordTypeId;
    private String recordTypeKey;
    private String recordTypeName;
    private UUID mappingProfileId;
    private String mappingProfileKey;
    private String mappingProfileName;
    private String mappingMode;
    private String status;
    private String failureStage;
    private String errorCode;
    private String errorMessage;
    private String payloadHash;
    private String idempotencyKey;
    private UUID recordId;
    private UUID eventId;
    private Integer responseStatus;
    private Instant receivedAt;
    private Instant createdAt;
    private Instant updatedAt;
    // idempotency applied flag derived
    private Boolean idempotencyApplied;
}
