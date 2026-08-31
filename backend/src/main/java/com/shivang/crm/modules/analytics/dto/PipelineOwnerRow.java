package com.shivang.crm.modules.analytics.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One aggregate row per deal owner for deals created within the selected
 * period and inside the caller's resolved analytics scope.
 *
 * The owner set is derived exclusively from the already-authorized record set
 * (the exact same predicates as the summary) - a client-supplied ownerId can
 * never widen the underlying records. Current-stage snapshot semantics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineOwnerRow {

    /** Owning user id (nullable for deals with no owner). */
    private UUID ownerUserId;

    /** Owner display name resolved from the user table (null when unassigned). */
    private String ownerDisplayName;

    /** Deals owned by this owner whose recordCategory = OPEN. */
    private long openCount;

    /** Deals owned by this owner whose recordCategory = CLOSED_WON. */
    private long wonCount;

    /** Deals owned by this owner whose recordCategory = CLOSED_LOST. */
    private long lostCount;

    /** SUM(amount) of the open deals owned by this owner. */
    private BigDecimal pipelineValue;

    /** SUM(amount) of the won deals owned by this owner. */
    private BigDecimal wonValue;

    /** Total deals owned by this owner. */
    private long totalCount;
}