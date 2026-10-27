package com.shivang.crm.modules.account.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.account.dto.AccountResponse;
import com.shivang.crm.modules.account.dto.Customer360Response;
import com.shivang.crm.modules.account.dto.Customer360Section;
import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.contact.dto.ContactResponse;
import com.shivang.crm.modules.deal.dto.DealResponse;
import com.shivang.crm.modules.deal.service.DealService;
import com.shivang.crm.modules.entitlement.dto.CustomerEntitlementResponse;
import com.shivang.crm.modules.entitlement.service.CustomerEntitlementService;
import com.shivang.crm.modules.feedback.dto.FeedbackListParams;
import com.shivang.crm.modules.feedback.dto.FeedbackResponse;
import com.shivang.crm.modules.feedback.service.FeedbackService;
import com.shivang.crm.modules.ticket.dto.TicketResponse;
import com.shivang.crm.modules.ticket.service.TicketService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.2-P1/P2/P3: Customer 360 read foundation.
 *
 * <p>A live aggregation, deliberately not a projection. The L3.2-P0 audit found
 * that Account and Contact publish no canonical events at all, so an event-driven
 * read model could not be built or rebuilt correctly today.
 *
 * <p>Account stays the canonical customer identity; this service owns no domain
 * data and adds no authorization model. Every section delegates to the domain
 * service that already owns its authorization, so each collection keeps its own
 * OWN/TEAM/ALL semantics.
 *
 * <p>That per-section independence is the security property, not an accident: a
 * caller who may read an Account may still not see a particular Deal or Ticket on
 * it. Reading the Account does not widen the Account's children, and every query
 * below is already account-filtered and scope-filtered by its own service, so no
 * query is tenant-wide.
 *
 * <p>P2 adds independent pagination per section. Page and size reach the domain
 * services, so the database does the paging; nothing is truncated here.
 *
 * <p>P3 adds the timeline, delegated to {@link Customer360TimelineService} so this
 * class stays an orchestrator. This service keeps ownership of clamping every
 * page/size pair, including the timeline's.
 *
 * <p>L3.3-P3 adds the feedback section. It delegates to {@link FeedbackService},
 * exactly like the ticket and entitlement sections delegate to theirs, so the
 * section inherits {@code feedback:read} and OWN/TEAM/ALL without restating them.
 * That also means it inherits their strictness: a caller with no feedback scope
 * fails the whole request, just as a caller with no ticket scope already does.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Customer360Service {

    /** Matches the repository's existing list-endpoint bounds (see TicketService). */
    static final int DEFAULT_SECTION_SIZE = 20;
    static final int MAX_SECTION_SIZE = 100;

    private final AccountService accountService;
    private final DealService dealService;
    private final CustomerEntitlementService customerEntitlementService;
    private final TicketService ticketService;
    private final FeedbackService feedbackService;
    private final Customer360TimelineService customer360TimelineService;

    @Transactional(readOnly = true)
    public Customer360Response getAccount360(
            UUID accountId,
            UUID tenantId,
            int contactsPage, int contactsSize,
            int dealsPage, int dealsSize,
            int entitlementsPage, int entitlementsSize,
            int ticketsPage, int ticketsSize,
            int feedbackPage, int feedbackSize,
            int timelinePage, int timelineSize) {

        // Authorizes account:read and the Account's own record scope, and throws the
        // existing NOT_FOUND for a missing, cross-tenant or deleted Account. No child
        // query runs until this passes.
        AccountResponse account = accountService.getAccountById(accountId, tenantId);

        int cPage = safePage(contactsPage);
        int cSize = safeSize(contactsSize);
        int dPage = safePage(dealsPage);
        int dSize = safeSize(dealsSize);
        int ePage = safePage(entitlementsPage);
        int eSize = safeSize(entitlementsSize);
        int tPage = safePage(ticketsPage);
        int tSize = safeSize(ticketsSize);
        int fPage = safePage(feedbackPage);
        int fSize = safeSize(feedbackSize);
        int tlPage = safePage(timelinePage);
        int tlSize = safeSize(timelineSize);

        Page<ContactResponse> contacts =
                accountService.listContacts(accountId, tenantId, cPage, cSize);

        Page<DealResponse> deals = dealService.listDeals(
                tenantId, null, accountId, null, null, null, null, null, null, null, dPage, dSize);

        Page<CustomerEntitlementResponse> entitlements = customerEntitlementService.list(
                tenantId, accountId, null, null, null, null, null, null, null, null, ePage, eSize);

        Page<TicketResponse> tickets = ticketService.listTickets(
                tenantId, accountId, null, null, tPage, tSize);

        // L3.3-P3: the feedback section. Delegated, not reimplemented: FeedbackService
        // applies feedback:read and OWN/TEAM/ALL, so this section cannot show a caller
        // feedback they could not read through GET /feedback. Strict like the ticket
        // and entitlement sections — no scope, no 360.
        Page<FeedbackResponse> feedback = feedbackService.list(
                tenantId,
                FeedbackListParams.builder()
                        .accountId(accountId)
                        .page(fPage)
                        .size(fSize)
                        .build());

        log.debug("Customer 360 for account {}: contacts {}/{}, deals {}/{}, entitlements {}/{}, tickets {}/{}, feedback {}/{}, timeline {}/{}",
                accountId, cPage, cSize, dPage, dSize, ePage, eSize, tPage, tSize, fPage, fSize, tlPage, tlSize);

        // Timeline runs last: it resolves the same read scopes, so resolving them
        // only after the sections have succeeded keeps an unsupported timeline from
        // changing the outcome of a request whose sections already answered.
        Page<ActivityResponse> timeline = customer360TimelineService.getAccountTimeline(
                tenantId, accountId, tlPage, tlSize);

        return Customer360Response.builder()
                .account(account)
                .contacts(Customer360Section.of(contacts))
                .deals(Customer360Section.of(deals))
                .entitlements(Customer360Section.of(entitlements))
                .tickets(Customer360Section.of(tickets))
                .feedback(Customer360Section.of(feedback))
                .timeline(Customer360Section.of(timeline))
                .build();
    }

    /**
     * Deal and Entitlement list methods build a raw {@code PageRequest.of(page, size)},
     * which rejects a negative page or a non-positive size. Clamping here keeps a bad
     * query parameter a defaulted request rather than a 500, and bounds each query.
     */
    private static int safePage(int page) {
        return Math.max(0, page);
    }

    private static int safeSize(int size) {
        return Math.min(MAX_SECTION_SIZE, size <= 0 ? DEFAULT_SECTION_SIZE : size);
    }
}
