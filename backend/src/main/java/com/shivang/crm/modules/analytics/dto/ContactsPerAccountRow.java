package com.shivang.crm.modules.analytics.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AN-16 B3: contacts grouped by their account for contacts created in the
 * selected period and inside the caller's resolved analytics scope. The group
 * dimension is derived from each contact's own authorized account_id - never
 * from an account filter. Contacts with a null/unknown account fall into the
 * stable NO ACCOUNT bucket so the summed contactCount reconciles with
 * summary.contacts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContactsPerAccountRow {

    /** Account owning the aggregated contacts (null for the NO ACCOUNT bucket). */
    private UUID accountId;

    /** Account name, or the stable "NO ACCOUNT" bucket. */
    private String accountName;

    /** Contacts created in the period and in scope that belong to this account. */
    private long contactCount;
}
