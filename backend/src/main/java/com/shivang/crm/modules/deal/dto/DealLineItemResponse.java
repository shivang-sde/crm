package com.shivang.crm.modules.deal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Deal line item response payload")
public class DealLineItemResponse {

    private UUID id;

    private UUID dealId;

    private UUID offeringId;

    private String itemName;

    private String itemCode;

    private String description;
    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private BigDecimal discountAmount;

    private BigDecimal taxAmount;

    /**
     * L3.0-P4-F: currency of the monetary values on this line, snapshotted from
     * the Offering. Null when the Offering has no currency configured.
     */
    private String currencyCode;

    private BigDecimal lineTotal;

    private LocalDate serviceStartDate;

    private LocalDate serviceEndDate;

    private Boolean renewable;

    private Integer renewalNoticeDays;

    private Map<String, Object> customData;

    private Instant createdAt;

    private Instant updatedAt;
}
