package com.shivang.crm.modules.catalog.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.catalog.enums.BillingInterval;
import com.shivang.crm.modules.catalog.enums.BillingType;
import com.shivang.crm.modules.catalog.enums.OfferingType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "OfferingCreateRequest", description = "Request to create a catalog offering")
public class OfferingCreateRequest {

    @Schema(example = "Premium Support")
    private String name;

    @Schema(example = "PREMIUM_SUPPORT")
    private String code;

    @Schema(example = "Priority support for enterprise customers")
    private String description;

    @Schema(example = "SERVICE")
    private OfferingType offeringType;

    @Schema(example = "RECURRING")
    private BillingType billingType;

    @Schema(example = "MONTHLY")
    private BillingInterval billingInterval;

    @Schema(example = "199.00")
    private BigDecimal defaultPrice;

    @Schema(example = "USD")
    private String currencyCode;

    @Schema(example = "30")
    private Integer defaultTermDays;

    @Schema(example = "false")
    private Boolean renewable;

    @Schema(example = "true")
    private Boolean active;

    @Schema(description = "Owner user UUID")
    private UUID ownerUserId;

    @Schema(description = "Custom fields data")
    @JsonProperty("customData")
    private Map<String, Object> customData;
}
