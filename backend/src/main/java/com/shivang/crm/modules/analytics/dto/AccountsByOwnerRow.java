package com.shivang.crm.modules.analytics.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Accounts created in the selected period grouped by their owning user. The
 * group dimension (account.owner_user_id) is derived exclusively from each
 * account inside the caller's already-authorized account scope - never from a
 * client-supplied owner filter - so an owner id can never widen the underlying
 * records. Accounts with no owner fall into the UNASSIGNED bucket so the
 * summed accountCount reconciles with the authorized account population.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountsByOwnerRow {

    /** Owning user id (null for accounts with no owner). */
    private UUID ownerUserId;

    /** Owner display name resolved from the user table (null when unassigned). */
    private String ownerDisplayName;

    /** Accounts created in the period and in scope owned by this owner. */
    private long accountCount;

    /** Of {@link #accountCount}, the count with is_active = true. */
    private long activeCount;
}
