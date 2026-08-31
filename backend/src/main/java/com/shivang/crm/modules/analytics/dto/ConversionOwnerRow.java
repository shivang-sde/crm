package com.shivang.crm.modules.analytics.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One aggregate row per lead owner using the same AN-10.1 conversion
 * semantics as the summary:
 *   newLeadCount        = leads created in the selected period, owned by this owner
 *   convertedLeadCount  = those same created-window leads that have since
 *                         converted (isConverted = true, convertedAt NOT NULL)
 *   conversionRate      = convertedLeadCount / newLeadCount * 100, or 0 when newLeadCount = 0
 *
 * Grouping is derived only from the already-authorized lead set; a
 * client-supplied ownerId can never widen it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversionOwnerRow {

    /** Owning user id (nullable for leads with no owner). */
    private UUID ownerUserId;

    /** Owner display name resolved from the user table (null when unassigned). */
    private String ownerDisplayName;

    /** Leads created in the selected period, owned by this owner. */
    private long newLeadCount;

    /** Of newLeadCount, those that have since converted. */
    private long convertedLeadCount;

    /** convertedLeadCount / newLeadCount * 100, or 0 when newLeadCount = 0. */
    private double conversionRate;
}