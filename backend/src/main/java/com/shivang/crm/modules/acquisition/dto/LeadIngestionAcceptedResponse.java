package com.shivang.crm.modules.acquisition.dto;

import java.time.Instant;
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
@Schema(name = "LeadIngestionAcceptedResponse", description = "Acknowledgement for accepted lead ingestion event")
public class LeadIngestionAcceptedResponse {

    @JsonProperty("eventId")
    private UUID eventId;

    @JsonProperty("status")
    private LeadIngestionEventStatus status;

    @JsonProperty("receivedAt")
    private Instant receivedAt;
}
