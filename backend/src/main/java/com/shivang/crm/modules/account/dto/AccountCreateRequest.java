package com.shivang.crm.modules.account.dto;

import java.math.BigDecimal;
import java.util.Map;
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
@Schema(name = "AccountCreateRequest", description = "Request to create a new account")
public class AccountCreateRequest {

    @NotBlank(message = "Account name is required")
    @Schema(example = "ABC Pvt Ltd")
    private String name;

    @Schema(example = "https://abc.com")
    private String website;

    @Schema(example = "Technology")
    private String industry;

    @Schema(example = "+91-9876543210")
    private String phone;

    @Schema(example = "contact@abc.com")
    private String email;

    @Schema(example = "1200000.00")
    private BigDecimal annualRevenue;

    @Schema(example = "300")
    private Integer employeeCount;

    @Schema(example = "A growing technology company")
    private String description;

    @Schema(example = "India")
    private String country;

    @Schema(example = "Maharashtra")
    private String state;

    @Schema(example = "Mumbai")
    private String city;

    @Schema(example = "123 Business Street")
    private String addressLine1;

    @Schema(example = "400001")
    private String postalCode;

    @Schema(description = "Account owner user UUID")
    private UUID ownerUserId;

    @Schema(description = "Lead UUID this account was created from")
    private UUID leadId;

    @Schema(description = "Custom fields data")
    @JsonProperty("customData")
    private Map<String, Object> customData;
}
