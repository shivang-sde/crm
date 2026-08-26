package com.shivang.crm.modules.lead.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LeadConvertRequest", description = "Request to convert a lead into an existing or new account/contact, optionally creating a deal")
public class LeadConvertRequest {

    @Schema(description = "Existing account UUID to use when converting the lead")
    private UUID accountId;

    @Schema(description = "Existing contact UUID to use when converting the lead")
    private UUID contactId;

    @Schema(description = "Whether to also create a deal for this lead during conversion")
    private Boolean createDeal;

    @Schema(description = "Optional name for the created deal; derived from the lead when omitted")
    private String dealName;

    @Schema(description = "Optional amount for the created deal")
    private BigDecimal dealAmount;
}
