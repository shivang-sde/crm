package com.shivang.crm.modules.lead.dto;

import java.time.Instant;
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
@Schema(name = "LeadSourceResponse", description = "Lead source details")
public class LeadSourceResponse {

    @JsonProperty("id")
    @Schema(description = "Unique identifier")
    private UUID id;

    @JsonProperty("name")
    @Schema(example = "Website")
    private String name;

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonProperty("createdAt")
    private Instant createdAt;
}
