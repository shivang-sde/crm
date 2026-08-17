package com.shivang.crm.modules.acquisition.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.acquisition.config.LeadIngestionTransportType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LeadIngestionConfigResponse", description = "Lead ingestion configuration response")
public class LeadIngestionConfigResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("transportType")
    private LeadIngestionTransportType transportType;

    @JsonProperty("publicKey")
    private String publicKey;

    @JsonProperty("inboundPath")
    private String inboundPath;

    @JsonProperty("active")
    private Boolean active;

    @JsonProperty("settings")
    private Map<String, Object> settings;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;
}