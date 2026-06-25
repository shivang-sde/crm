package com.shivang.crm.modules.lead.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.activity.dto.ActivityResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LeadDetailResponse", description = "Complete lead details with timeline and notes")
public class LeadDetailResponse {

    @JsonProperty("id")
    @Schema(description = "Unique identifier")
    private UUID id;

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("email")
    private String email;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("company")
    private String company;

    @JsonProperty("status")
    private LeadStatusResponse status;

    @JsonProperty("source")
    private LeadSourceResponse source;

    @JsonProperty("ownerUserId")
    private UUID ownerUserId;

    @JsonProperty("score")
    private Integer score;

    @JsonProperty("isConverted")
    private Boolean isConverted;

    @JsonProperty("convertedAt")
    private Instant convertedAt;

    @JsonProperty("customData")
    @Schema(description = "Custom fields data")
    private Map<String, Object> customData;

    @JsonProperty("createdBy")
    private UUID createdBy;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;

    // Additional detail page fields
    @JsonProperty("activities")
    @Schema(description = "Recent activities")
    private List<ActivityResponse> activities;

    @JsonProperty("notes")
    @Schema(description = "Lead notes")
    private List<EntityNoteResponse> notes;
}
