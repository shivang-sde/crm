package com.shivang.crm.modules.activity.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ActivityResponse", description = "Activity record for an entity")
public class ActivityResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("entityType")
    private String entityType;

    @JsonProperty("entityId")
    private UUID entityId;

    @JsonProperty("activityType")
    private String activityType;

    @JsonProperty("description")
    private String description;

    @JsonProperty("performedBy")
    private UUID performedBy;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @JsonProperty("createdAt")
    private Instant createdAt;
}
