package com.shivang.crm.modules.account.dto;

import java.math.BigDecimal;
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
@Schema(name = "AccountUpdateRequest", description = "Request to update an existing account")
public class AccountUpdateRequest {

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

    @Schema(description = "Custom fields data")
    @JsonProperty("customData")
    private Map<String, Object> customData;
}
