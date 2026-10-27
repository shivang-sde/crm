package com.shivang.crm.modules.deal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.shivang.crm.modules.deal.entity.DealType;
import com.shivang.crm.modules.deal.entity.ForecastCategory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Request payload for creating a new deal")
public class DealCreateRequest {

    @NotBlank(message = "Deal name is required")
    @Schema(description = "Deal name", example = "Enterprise Solution Deal")
    private String name;

    @Schema(description = "Deal stage UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID stageId;

    @Schema(description = "Associated account UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID accountId;

    @Schema(description = "Associated contact UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID contactId;

    @Schema(description = "Associated lead UUID (for lead-to-deal conversion)", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID leadId;

    @Schema(description = "Deal amount", example = "150000.00")
    private BigDecimal amount;

    @Schema(description = "Expected close date", example = "2026-12-31")
    private LocalDate expectedCloseDate;

    @Schema(description = "Actual closed date")
    private LocalDate closedDate;

    @Schema(description = "Probability percentage (0-100)", example = "75")
    private Integer probability;

    @Schema(description = "Forecast category")
    private ForecastCategory forecastCategory;

    @Schema(description = "Next step")
    private String nextStep;

    @Schema(description = "Deal type")
    private DealType dealType;

    @Schema(description = "Lead source")
    private String leadSource;

    @Schema(description = "Campaign source")
    private String campaignSource;

    @Schema(description = "Reason why the deal was won")
    private String wonReason;

    @Schema(description = "Reason why the deal was lost")
    private String lostReason;

    @Schema(description = "Deal description")
    private String description;

    @Schema(description = "Deal owner user UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID ownerUserId;

    @Schema(description = "Custom fields as key-value map")
    private Map<String, Object> customData;

    /**
     * L3.0-P5: the Deal's initial commercial lines, created in the same
     * transaction as the Deal itself.
     *
     * <p>This exists because a Deal cannot be created directly in CLOSED_WON and
     * then given its lines: a commercially closed Deal has no legitimate window
     * in which lines may be added. Carrying them here means a won Deal is born
     * with its coverage already attached, instead of being opened up to be
     * sold and re-closed.
     *
     * <p>Optional for OPEN and CLOSED_LOST Deals. Required and non-empty for a
     * CLOSED_WON Deal, because a won Deal with no lines could never produce the
     * entitlements that represent what the customer was sold.
     *
     * <p>Deliberately absent from DealUpdateRequest. Line-item mutation stays
     * owned by DealLineItemService so that a Deal update can never silently
     * read as "replace all of this Deal's lines".
     */
    @Schema(description = "Initial line items, created atomically with the deal")
    @Valid
    private List<DealLineItemCreateRequest> lineItems;
}
