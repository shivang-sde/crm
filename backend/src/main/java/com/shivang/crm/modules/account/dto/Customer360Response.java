package com.shivang.crm.modules.account.dto;


import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.contact.dto.ContactResponse;
import com.shivang.crm.modules.deal.dto.DealResponse;
import com.shivang.crm.modules.entitlement.dto.CustomerEntitlementResponse;
import com.shivang.crm.modules.feedback.dto.FeedbackResponse;
import com.shivang.crm.modules.ticket.dto.TicketResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * L3.2-P1/P2: Customer 360 read model for one Account.
 *
 * <p>Account remains the canonical customer identity; this DTO is a view over
 * existing domains and owns none of them. Each section reuses its own domain
 * response shape rather than a flattened copy, so the contract cannot drift from
 * the entity endpoints it reuses.
 *
 * <p>L3.2-P2: each child collection became a {@link Customer360Section}, because a
 * bare list cannot say whether records were omitted. Every section reports its own
 * page, size and totals, so the collections paginate independently.
 *
 * <p>L3.3-P3 adds the {@code feedback} section, in the same shape, for the same
 * reason.
 *
 * <p>An empty {@code items} with {@code total = 0} means the caller can see nothing
 * of that type. Under OWN or TEAM scope it may equally mean records exist that the
 * caller cannot see — the totals describe visibility, not the underlying data.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Customer 360 view of an Account and its visible related records")
public class Customer360Response {

    @Schema(description = "The Account this view is anchored on")
    private AccountResponse account;

    @Schema(description = "Contacts on this Account that the caller may read")
    private Customer360Section<ContactResponse> contacts;

    @Schema(description = "Deals on this Account within the caller's deal:read scope")
    private Customer360Section<DealResponse> deals;

    @Schema(description = "Entitlements on this Account within the caller's entitlement:read scope")
    private Customer360Section<CustomerEntitlementResponse> entitlements;

    @Schema(description = "Support tickets on this Account within the caller's ticket:read scope")
    private Customer360Section<TicketResponse> tickets;

    /**
     * L3.3-P3. Customer feedback on this Account within the caller's feedback:read
     * scope, newest first.
     *
     * <p>The section reuses {@link FeedbackResponse}, the same DTO the feedback
     * endpoints return, so the contract cannot drift from them. {@code ticketId} is
     * exposed when a complaint has been operationally linked, which is how the later
     * frontend phase navigates from a complaint to its ticket; the ticket itself is
     * not nested here, because the tickets section already carries it.
     */
    @Schema(description = "Customer feedback on this Account within the caller's feedback:read scope")
    private Customer360Section<FeedbackResponse> feedback;

    /**
     * L3.2-P3. Newest-first activities across this Account and the child records the
     * caller can see, read from the existing {@code activities} table.
     *
     * <p>Types: ACCOUNT, CONTACT, DEAL, TICKET, ENTITLEMENT — the same five entities
     * aggregated above. An activity appears only when the record it belongs to is
     * visible to the caller, so a record excluded from a section above cannot leak
     * its history through the timeline.
     */
    @Schema(description = "Unified newest-first activity timeline across the Account and its visible records")
    private Customer360Section<ActivityResponse> timeline;
}
