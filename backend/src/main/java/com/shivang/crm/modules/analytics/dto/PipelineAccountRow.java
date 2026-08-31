package com.shivang.crm.modules.analytics.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One aggregate row per account for deals created within the selected period
 * and inside the caller's resolved analytics scope. Accounts only appear when
 * they own at least one qualifying, in-scope deal; an account id can never be
 * used to bypass the authorized record set. Current-stage snapshot semantics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineAccountRow {

    /** Account id owning the aggregated deals. */
    private UUID accountId;

    /** Account name resolved from the accounts table. */
    private String accountName;

    /** Deals for this account whose recordCategory = OPEN. */
    private long openCount;

    /** Deals for this account whose recordCategory = CLOSED_WON. */
    private long wonCount;

    /** Deals for this account whose recordCategory = CLOSED_LOST. */
    private long lostCount;

    /** SUM(amount) of the open deals for this account. */
    private BigDecimal pipelineValue;

    /** SUM(amount) of the won deals for this account. */
    private BigDecimal wonValue;

    /** Total qualifying deals for this account. */
    private long totalCount;
}