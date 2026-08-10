package com.shivang.crm.modules.deal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    @JsonProperty("deal_id")
    private UUID dealId;

    @JsonProperty("offering_id")
    private UUID offeringId;

    @JsonProperty("item_name")
    private String itemName;

    @JsonProperty("item_code")
    private String itemCode;

    private String description;
    private BigDecimal quantity;

    @JsonProperty("unit_price")
    private BigDecimal unitPrice;

    @JsonProperty("discount_amount")
    private BigDecimal discountAmount;

    @JsonProperty("tax_amount")
    private BigDecimal taxAmount;

    @JsonProperty("line_total")
    private BigDecimal lineTotal;

    @JsonProperty("service_start_date")
    private LocalDate serviceStartDate;

    @JsonProperty("service_end_date")
    private LocalDate serviceEndDate;

    private Boolean renewable;

    @JsonProperty("renewal_notice_days")
    private Integer renewalNoticeDays;

    @JsonProperty("custom_data")
    private Map<String, Object> customData;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
