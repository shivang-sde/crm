package com.shivang.crm.modules.entitlement.dto;

import java.time.LocalDate;
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
public class UpcomingRenewalResponse {

    @JsonProperty("entitlement_id")
    private UUID entitlementId;

    private String name;

    @JsonProperty("account_id")
    private UUID accountId;

    @JsonProperty("contact_id")
    private UUID contactId;

    @JsonProperty("offering_id")
    private UUID offeringId;

    @JsonProperty("owner_user_id")
    private UUID ownerUserId;

    @JsonProperty("start_date")
    private LocalDate startDate;

    @JsonProperty("end_date")
    private LocalDate endDate;

    @JsonProperty("renewal_due_date")
    private LocalDate renewalDueDate;

    @JsonProperty("days_until_expiry")
    private Integer daysUntilExpiry;

    private EntitlementStatus status;

    private Boolean renewable;
}
