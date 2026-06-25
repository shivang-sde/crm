package com.shivang.crm.modules.lead.dto;

import java.time.Instant;
import java.util.List;
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
@Schema(name = "LeadResponse", description = "Lead entity response")
public class LeadResponse {

    @JsonProperty("id")
    @Schema(description = "Unique identifier of the lead")
    private UUID id;

    @JsonProperty("firstName")
    @Schema(example = "John")
    private String firstName;

    @JsonProperty("lastName")
    @Schema(example = "Doe")
    private String lastName;

    @JsonProperty("email")
    @Schema(example = "john@example.com")
    private String email;

    @JsonProperty("phone")
    @Schema(example = "+91-9876543210")
    private String phone;

    @JsonProperty("company")
    @Schema(example = "ABC Corporation")
    private String company;

    @JsonProperty("status")
    @Schema(description = "Lead status details")
    private LeadStatusResponse status;

    @JsonProperty("source")
    @Schema(description = "Lead source details")
    private LeadSourceResponse source;

    @JsonProperty("ownerUserId")
    @Schema(description = "UUID of the lead owner")
    private UUID ownerUserId;

    @JsonProperty("score")
    @Schema(example = "50")
    private Integer score;

    private List<EntityNoteResponse> notes;

    @JsonProperty("isConverted")
    @Schema(example = "false")
    private Boolean isConverted;

    @JsonProperty("convertedAt")
    private Instant convertedAt;

    @JsonProperty("convertedAccountId")
    private UUID convertedAccountId;

    @JsonProperty("convertedContactId")
    private UUID convertedContactId;

    @JsonProperty("customData")
    @Schema(description = "Custom fields data")
    private Map<String, Object> customData;

    @JsonProperty("createdBy")
    private UUID createdBy;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;
}
