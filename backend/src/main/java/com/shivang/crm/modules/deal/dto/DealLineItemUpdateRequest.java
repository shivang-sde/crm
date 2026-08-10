package com.shivang.crm.modules.deal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

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
@Schema(description = "Request payload for updating a deal line item")
public class DealLineItemUpdateRequest {

    @Schema(description = "Quantity of the line item")
    private BigDecimal quantity;

    @Schema(description = "Unit price for the line item")
    @JsonProperty("unit_price")
    private BigDecimal unitPrice;

    @Schema(description = "Discount amount applied to the line item")
    @JsonProperty("discount_amount")
    private BigDecimal discountAmount;

    @Schema(description = "Tax amount applied to the line item")
    @JsonProperty("tax_amount")
    private BigDecimal taxAmount;

    @Schema(description = "Service start date")
    @JsonProperty("service_start_date")
    private LocalDate serviceStartDate;

    @Schema(description = "Service end date")
    @JsonProperty("service_end_date")
    private LocalDate serviceEndDate;

    @Schema(description = "Whether the service is renewable")
    private Boolean renewable;

    @Schema(description = "Renewal notice period in days")
    @JsonProperty("renewal_notice_days")
    private Integer renewalNoticeDays;

    @Schema(description = "Line item description")
    private String description;

    @Schema(description = "Custom data for the line item")
    @JsonProperty("custom_data")
    private Map<String, Object> customData;
}
