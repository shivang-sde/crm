package com.shivang.crm.modules.entitlement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEntitlementUpdateRequest {
    private String name;
    private String description;
    private String code;

    @JsonProperty("start_date")
    private LocalDate startDate;

    @JsonProperty("end_date")
    private LocalDate endDate;

    private BigDecimal quantity;

    @JsonProperty("agreed_price")
    private BigDecimal agreedPrice;

    @JsonProperty("currency_code")
    private String currencyCode;

    private Boolean renewable;

    @JsonProperty("auto_renew")
    private Boolean autoRenew;

    @JsonProperty("renewal_notice_days")
    private Integer renewalNoticeDays;

    @JsonProperty("custom_data")
    private Map<String, Object> customData;

    @JsonProperty("owner_user_id")
    private UUID ownerUserId;
}
