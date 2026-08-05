package com.shivang.crm.modules.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
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
@Schema(name = "OfferingResponse", description = "Offering catalog response")
public class OfferingResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("tenantId")
    private UUID tenantId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("code")
    private String code;

    @JsonProperty("description")
    private String description;

    @JsonProperty("offeringType")
    private OfferingType offeringType;

    @JsonProperty("billingType")
    private BillingType billingType;

    @JsonProperty("billingInterval")
    private BillingInterval billingInterval;

    @JsonProperty("defaultPrice")
    private BigDecimal defaultPrice;

    @JsonProperty("currencyCode")
    private String currencyCode;

    @JsonProperty("defaultTermDays")
    private Integer defaultTermDays;

    @JsonProperty("renewable")
    private Boolean renewable;

    @JsonProperty("active")
    private Boolean active;

    @JsonProperty("ownerUserId")
    private UUID ownerUserId;

    @JsonProperty("ownerName")
    private String ownerName;

    @JsonProperty("customData")
    private Map<String, Object> customData;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;
}
