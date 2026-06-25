package com.shivang.crm.modules.lead.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "EntityNoteResponse", description = "Note attached to an entity such as Lead, Contact, etc.")
public class EntityNoteResponse {

    @JsonProperty("id")
    @Schema(description = "Unique identifier")
    private UUID id;

    @JsonProperty("entityId")
    @Schema(description = "Entity UUID")
    private UUID entityId;

    @JsonProperty("entityType")
    @Schema(example = "LEAD", description = "Type of the entity")
    private String entityType;

    @NotBlank(message = "Note content is required")
    @JsonProperty("note")
    @Schema(example = "Customer interested in demo on Friday")
    private String note;

    @JsonProperty("createdBy")
    @Schema(description = "UUID of user who created the note")
    private UUID createdBy;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;
}
