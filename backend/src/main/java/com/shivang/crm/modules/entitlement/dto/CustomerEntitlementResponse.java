package com.shivang.crm.modules.entitlement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEntitlementResponse {
    private UUID id;
    private UUID tenantId;

    @JsonProperty("account_id")
    private UUID accountId;

    @JsonProperty("contact_id")
    private UUID contactId;

    @JsonProperty("offering_id")
    private UUID offeringId;

    @JsonProperty("deal_id")
    private UUID dealId;

    @JsonProperty("deal_line_item_id")
    private UUID dealLineItemId;

    private String name;
    private String code;
    private String description;
    private EntitlementStatus status;

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

    @JsonProperty("renewal_due_date")
    private LocalDate renewalDueDate;

    @JsonProperty("renewed_from_entitlement_id")
    private UUID renewedFromEntitlementId;

    @JsonProperty("renewed_to_entitlement_id")
    private UUID renewedToEntitlementId;

    @JsonProperty("renewal_deal_id")
    private UUID renewalDealId;

    @JsonProperty("custom_data")
    private Map<String, Object> customData;

    @JsonProperty("owner_user_id")
    private UUID ownerUserId;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
