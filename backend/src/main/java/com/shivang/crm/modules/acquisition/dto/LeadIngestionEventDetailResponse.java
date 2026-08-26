package com.shivang.crm.modules.acquisition.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEventStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LeadIngestionEventDetailResponse", description = "Detailed lead ingestion event for troubleshooting and mapping UX")
public class LeadIngestionEventDetailResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("ingestionConfigId")
    private UUID ingestionConfigId;

    @JsonProperty("externalEventId")
    private String externalEventId;

    @JsonProperty("idempotencyKey")
    private String idempotencyKey;

    @JsonProperty("status")
    private LeadIngestionEventStatus status;

    @JsonProperty("leadId")
    private UUID leadId;

    @JsonProperty("errorCode")
    private String errorCode;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("receivedAt")
    private Instant receivedAt;

    @JsonProperty("processedAt")
    private Instant processedAt;

    @JsonProperty("rawPayload")
    private Map<String, Object> rawPayload;

    @JsonProperty("headers")
    private Map<String, Object> headers;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;
}
