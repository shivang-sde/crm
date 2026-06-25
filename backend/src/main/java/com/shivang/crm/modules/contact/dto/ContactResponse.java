package com.shivang.crm.modules.contact.dto;

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
@Schema(name = "ContactResponse", description = "Contact details response")
public class ContactResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("accountId")
    private UUID accountId;

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("email")
    private String email;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("mobile")
    private String mobile;

    @JsonProperty("jobTitle")
    private String jobTitle;

    @JsonProperty("department")
    private String department;

    @JsonProperty("ownerUserId")
    private UUID ownerUserId;

    @JsonProperty("leadId")
    private UUID leadId;

    @JsonProperty("customData")
    private Map<String, Object> customData;

    @JsonProperty("isPrimary")
    private Boolean isPrimary;

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;
}
