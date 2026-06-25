package com.shivang.crm.modules.lead.dto;

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
@Schema(name = "EntityHistoryResponse", description = "History record for an entity")
public class EntityHistoryResponse {

    @JsonProperty("id")
    @Schema(description = "Unique identifier")
    private UUID id;

    @JsonProperty("entityId")
    @Schema(description = "Entity UUID")
    private UUID entityId;

    @JsonProperty("entityType")
    @Schema(example = "LEAD", description = "Type of the entity")
    private String entityType;

    @JsonProperty("eventType")
    @Schema(example = "STATUS_CHANGED", description = "LEAD_CREATED, STATUS_CHANGED, OWNER_CHANGED, NOTE_ADDED, etc.")
    private String eventType;

    @JsonProperty("description")
    @Schema(example = "Status changed from New to Contacted")
    private String description;

    @JsonProperty("performedBy")
    @Schema(description = "UUID of user who performed the action")
    private UUID performedBy;

    @JsonProperty("changes")
    @Schema(description = "Changes made to the lead")
    private Map<String, Object> changes;

    @JsonProperty("createdAt")
    private Instant createdAt;
}
