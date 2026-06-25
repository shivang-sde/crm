package com.shivang.crm.modules.account.dto;

import java.math.BigDecimal;
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
@Schema(name = "AccountResponse", description = "Account details response")
public class AccountResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("website")
    private String website;

    @JsonProperty("industry")
    private String industry;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("email")
    private String email;

    @JsonProperty("annualRevenue")
    private BigDecimal annualRevenue;

    @JsonProperty("employeeCount")
    private Integer employeeCount;

    @JsonProperty("description")
    private String description;

    @JsonProperty("country")
    private String country;

    @JsonProperty("state")
    private String state;

    @JsonProperty("city")
    private String city;

    @JsonProperty("addressLine1")
    private String addressLine1;

    @JsonProperty("postalCode")
    private String postalCode;

    @JsonProperty("ownerUserId")
    private UUID ownerUserId;

    @JsonProperty("leadId")
    private UUID leadId;

    @JsonProperty("customData")
    private Map<String, Object> customData;

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;
}
