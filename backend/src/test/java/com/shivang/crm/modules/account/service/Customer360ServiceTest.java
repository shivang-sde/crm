package com.shivang.crm.modules.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shivang.crm.modules.account.dto.AccountResponse;
import com.shivang.crm.modules.account.dto.Customer360Response;
import com.shivang.crm.modules.account.dto.Customer360Section;
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
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.PermissionDeniedException;

/**
 * L3.2-P2: Customer 360 pagination and orchestration.
 *
 * <p>The property under test is that Account visibility and child visibility are
 * independent, and that each section is paged in the database rather than truncated
 * in Java. Page and size are asserted to reach the domain services unchanged, because
 * a section that is fetched at the right page and then sliced would still be correct
 * on the first page and wrong on every other.
 */
class Customer360ServiceTest {

    private AccountService accountService;
    private DealService dealService;
    private CustomerEntitlementService entitlementService;
    private TicketService ticketService;
    private FeedbackService feedbackService;
private Customer360TimelineService timelineService;
    private Customer360Service service;

    private UUID tenantId;
    private UUID accountId;

    @BeforeEach
    void setup() {
        accountService = mock(AccountService.class);
        dealService = mock(DealService.class);
        entitlementService = mock(CustomerEntitlementService.class);
        ticketService = mock(TicketService.class);
        feedbackService = mock(FeedbackService.class);
        timelineService = mock(Customer360TimelineService.class);
        service = new Customer360Service(
                accountService, dealService, entitlementService, ticketService,
                feedbackService, timelineService);

        tenantId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        when(accountService.getAccountById(accountId, tenantId))
                .thenReturn(AccountResponse.builder().id(accountId).name("Acme").build());
        when(accountService.listContacts(any(), any(), anyInt(), anyInt())).thenReturn(page(0, 20));
        when(dealService.listDeals(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(page(0, 20));
        when(entitlementService.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(page(0, 20));
when(ticketService.listTickets(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page(0, 20));
        when(feedbackService.list(any(), any())).thenReturn(page(0, 20));
        when(timelineService.getAccountTimeline(any(), any(), anyInt(), anyInt()))
                .thenReturn(page(0, 20));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null, List.of()));
    }

    private <T> Page<T> page(int number, int size) {
        return new PageImpl<>(List.of(), PageRequest.of(number, size), 0);
    }

    private <T> Page<T> pageOf(List<T> content, int number, int size, long total) {
        return new PageImpl<>(content, PageRequest.of(number, size), total);
    }

    private Customer360Response call() {
        return service.getAccount360(accountId, tenantId, 0, 20, 0, 20, 0, 20, 0, 20, 0, 20, 0, 20);
    }

    private String codeOf(Throwable thrown) {
        return ((BusinessException) thrown).getErrorCode();
    }

    // ================= A. Account boundary =================

    @Test
    void anAccessibleAccountSucceeds() {
        assertThat(call()).isNotNull();
    }

    @Test
    void theAccountIsResolvedThroughTheExistingAuthorization() {
        call();

        verify(accountService).getAccountById(accountId, tenantId);
    }

    @Test
    void aCrossTenantOrDeletedAccountFailsWithTheExistingNotFound() {
        when(accountService.getAccountById(accountId, tenantId))
                .thenThrow(new BusinessException("NOT_FOUND", "Account not found"));

        assertThat(codeOf(catchThrowable(() -> call()))).isEqualTo("NOT_FOUND");
    }

    @Test
    void anInaccessibleAccountRunsNoChildQuery() {
        when(accountService.getAccountById(accountId, tenantId))
                .thenThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"));

        assertThat(codeOf(catchThrowable(() -> call()))).isEqualTo("SCOPE_DENIED");

        // The Account is the authorization boundary; nothing downstream may run first.
        verifyNoInteractions(dealService, entitlementService, ticketService);
        verify(accountService, never()).listContacts(any(), any(), anyInt(), anyInt());
    }

    @Test
    void paginationParametersCannotBypassAccountAuthorization() {
        when(accountService.getAccountById(accountId, tenantId))
                .thenThrow(new BusinessException("NOT_FOUND", "Account not found"));

// Hostile pagination values must not be applied before authorization.
        catchThrowable(() -> service.getAccount360(
                accountId, tenantId, 999, 100, 999, 100, 999, 100, 999, 100, 999, 100, 999, 100));

        verifyNoInteractions(dealService, entitlementService, ticketService, feedbackService, timelineService);
    }

    // ================= B. Pagination reaches the domain services =================

    @Test
    void independentPaginationIsForwardedPerSection() {
service.getAccount360(accountId, tenantId,
                2, 10,      // contacts
                0, 50,      // deals
                3, 20,      // entitlements
                1, 25,      // tickets
                4, 12,      // feedback
                7, 15);     // timeline

        verify(accountService).listContacts(accountId, tenantId, 2, 10);
        verify(dealService).listDeals(eq(tenantId), isNull(), eq(accountId), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(50));
        verify(entitlementService).list(eq(tenantId), eq(accountId), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(3), eq(20));
        verify(timelineService).getAccountTimeline(tenantId, accountId, 7, 15);
        verify(ticketService).listTickets(eq(tenantId), eq(accountId), isNull(), isNull(), eq(1), eq(25));
        // Feedback is forwarded with its own page/size, built into FeedbackListParams.
        var feedbackCaptor = org.mockito.ArgumentCaptor.forClass(FeedbackListParams.class);
        verify(feedbackService).list(eq(tenantId), feedbackCaptor.capture());
        assertThat(feedbackCaptor.getValue().getAccountId()).isEqualTo(accountId);
        assertThat(feedbackCaptor.getValue().getPage()).isEqualTo(4);
        assertThat(feedbackCaptor.getValue().getSize()).isEqualTo(12);
    }

    @Test
    void paginationIsDatabaseLevelNotJavaSide() {
        service.getAccount360(accountId, tenantId, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 0, 20);

        // Each domain service receives its own page. Nothing is fetched at a large
        // size and then sliced, which would be correct only on page 0.
        verify(accountService).listContacts(accountId, tenantId, 4, 5);
        verify(dealService).listDeals(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(6), eq(7));
        verify(entitlementService).list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(8), eq(9));
        verify(ticketService).listTickets(any(), any(), any(), any(), eq(10), eq(11));
        var feedbackCaptor = org.mockito.ArgumentCaptor.forClass(FeedbackListParams.class);
        verify(feedbackService).list(any(), feedbackCaptor.capture());
        assertThat(feedbackCaptor.getValue().getPage()).isEqualTo(12);
        assertThat(feedbackCaptor.getValue().getSize()).isEqualTo(13);
    }

    @Test
    void everySectionAlwaysCarriesTheAccountId() {
        call();

        verify(accountService).listContacts(eq(accountId), any(), anyInt(), anyInt());
        verify(dealService).listDeals(any(), any(), eq(accountId), any(), any(), any(), any(), any(), any(),
                any(), anyInt(), anyInt());
        verify(entitlementService).list(any(), eq(accountId), any(), any(), any(), any(), any(), any(),
                any(), any(), anyInt(), anyInt());
        verify(ticketService).listTickets(any(), eq(accountId), any(), any(), anyInt(), anyInt());
        // Feedback is filtered by its own accountId column, never re-resolved.
        var feedbackCaptor = org.mockito.ArgumentCaptor.forClass(FeedbackListParams.class);
        verify(feedbackService).list(any(), feedbackCaptor.capture());
        assertThat(feedbackCaptor.getValue().getAccountId()).isEqualTo(accountId);
    }

    @Test
    void aSectionIsNeverQueriedWithoutItsAccountFilter() {
        call();

        // A null accountId would turn a scoped section into a tenant-wide query.
        verify(dealService, never()).listDeals(any(), any(), isNull(), any(), any(), any(), any(), any(),
                any(), any(), anyInt(), anyInt());
        verify(entitlementService, never()).list(any(), isNull(), any(), any(), any(), any(), any(), any(),
                any(), any(), anyInt(), anyInt());
        verify(ticketService, never()).listTickets(any(), isNull(), any(), any(), anyInt(), anyInt());
    }

    // ================= C. Defaults and clamping =================

    @Test
    void defaultBoundsMatchTheRepositoryListConvention() {
        assertThat(Customer360Service.DEFAULT_SECTION_SIZE).isEqualTo(20);
        assertThat(Customer360Service.MAX_SECTION_SIZE).isEqualTo(100);
    }

    @Test
    void aNegativePageIsClampedToZero() {
        // DealService and CustomerEntitlementService build a raw PageRequest.of, which
        // throws on a negative page. Clamping keeps a bad parameter a default, not a 500.
        service.getAccount360(accountId, tenantId, -5, 20, -5, 20, -5, 20, -5, 20, -5, 20, 0, 20);

        verify(accountService).listContacts(accountId, tenantId, 0, 20);
        verify(dealService).listDeals(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(0), eq(20));
        verify(entitlementService).list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(0), eq(20));
        verify(ticketService).listTickets(any(), any(), any(), any(), eq(0), eq(20));
    }

    @Test
    void aNonPositiveSizeFallsBackToTheDefault() {
        service.getAccount360(accountId, tenantId, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0, 20);

        verify(accountService).listContacts(accountId, tenantId, 0, 20);
        verify(dealService).listDeals(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(0), eq(20));
    }

    @Test
    void anOversizedRequestIsClampedToTheMaximum() {
        service.getAccount360(accountId, tenantId, 0, 50_000, 0, 50_000, 0, 50_000, 0, 50_000, 0, 50_000, 0, 20);

        // Bounded: this endpoint must never become an unbounded export.
        verify(accountService).listContacts(accountId, tenantId, 0, 100);
        verify(dealService).listDeals(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(0), eq(100));
        verify(entitlementService).list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(0), eq(100));
        verify(ticketService).listTickets(any(), any(), any(), any(), eq(0), eq(100));
    }

    // ================= D. Metadata =================

    @Test
    void everySectionCarriesItsOwnPaginationMetadata() {
        ContactResponse contact = new ContactResponse();
        DealResponse deal = new DealResponse();
        CustomerEntitlementResponse entitlement = new CustomerEntitlementResponse();
        TicketResponse ticket = new TicketResponse();

        when(accountService.listContacts(any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(List.of(contact), 1, 10, 37));
        when(dealService.listDeals(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(pageOf(List.of(deal), 0, 20, 143));
        when(entitlementService.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(pageOf(List.of(entitlement), 2, 20, 41));
        when(ticketService.listTickets(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(List.of(ticket), 0, 20, 64));

        Customer360Response result = service.getAccount360(
                accountId, tenantId, 1, 10, 0, 20, 2, 20, 0, 20, 0, 20, 0, 20);

        assertThat(result.getContacts().getPage()).isEqualTo(1);
        assertThat(result.getContacts().getSize()).isEqualTo(10);
        assertThat(result.getContacts().getTotal()).isEqualTo(37);
        assertThat(result.getContacts().getTotalPages()).isEqualTo(4);
        assertThat(result.getContacts().isHasNext()).isTrue();

        assertThat(result.getDeals().getTotal()).isEqualTo(143);
        assertThat(result.getDeals().getTotalPages()).isEqualTo(8);

        assertThat(result.getEntitlements().getPage()).isEqualTo(2);
        assertThat(result.getEntitlements().getTotal()).isEqualTo(41);

        assertThat(result.getTickets().getTotal()).isEqualTo(64);
        assertThat(result.getTickets().isHasNext()).isTrue();
    }

    @Test
    void hasNextIsFalseOnTheLastPage() {
        when(accountService.listContacts(any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(List.of(new ContactResponse()), 1, 20, 30));

        assertThat(call().getContacts().isHasNext()).isFalse();
    }

    @Test
    void hasNextIsFalseWhenTheSectionIsEmpty() {
        assertThat(call().getContacts().isHasNext()).isFalse();
        assertThat(call().getTickets().isHasNext()).isFalse();
    }

    @Test
    void anOutOfRangePageReturnsEmptyItemsWithAccurateMetadata() {
        // Page 10 of a 2-page collection. The domain service returns an empty page,
        // and the section must report page 10 — not silently substitute page 0.
        when(dealService.listDeals(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(pageOf(List.of(), 10, 20, 25));

        Customer360Section<DealResponse> deals =
                service.getAccount360(accountId, tenantId, 0, 20, 10, 20, 0, 20, 0, 20, 0, 20, 0, 20).getDeals();

        assertThat(deals.getItems()).isEmpty();
        assertThat(deals.getPage()).isEqualTo(10);
        assertThat(deals.getTotal()).isEqualTo(25);
        assertThat(deals.getTotalPages()).isEqualTo(2);
        assertThat(deals.isHasNext()).isFalse();
    }

    @Test
    void anEmptyAccountReturnsAValidEmptyPagePerSection() {
        Customer360Response result = call();

        assertThat(result.getAccount()).isNotNull();
        for (Customer360Section<?> section : List.of(
                result.getContacts(), result.getDeals(),
                result.getEntitlements(), result.getTickets(),
                result.getFeedback())) {
            assertThat(section).isNotNull();
            assertThat(section.getItems()).isEmpty();
            assertThat(section.getPage()).isZero();
            assertThat(section.getSize()).isEqualTo(20);
            assertThat(section.getTotal()).isZero();
            assertThat(section.getTotalPages()).isZero();
            assertThat(section.isHasNext()).isFalse();
        }
    }

    @Test
    void noSectionIsNull() {
        Customer360Response result = call();

        assertThat(result.getContacts()).isNotNull();
        assertThat(result.getDeals()).isNotNull();
        assertThat(result.getEntitlements()).isNotNull();
        assertThat(result.getTickets()).isNotNull();
        assertThat(result.getFeedback()).isNotNull();
    }

    @Test
    void anOversizedRequestIsBoundedBeforeItReachesTheDomain() {
        // The response echoes the size the domain service reports, so the bound is
        // verified where it is applied: on the outgoing call.
        service.getAccount360(accountId, tenantId, 0, 5000, 0, 5000, 0, 5000, 0, 5000, 0, 5000, 0, 5000);

        verify(accountService).listContacts(accountId, tenantId, 0, 100);
        verify(dealService).listDeals(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(0), eq(100));
        verify(entitlementService).list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(0), eq(100));
        verify(ticketService).listTickets(any(), any(), any(), any(), eq(0), eq(100));
        // Feedback is clamped by the same shared bound, not by its own service.
        var feedbackCaptor = org.mockito.ArgumentCaptor.forClass(FeedbackListParams.class);
        verify(feedbackService).list(any(), feedbackCaptor.capture());
        assertThat(feedbackCaptor.getValue().getSize()).isEqualTo(100);
    }

    // ================= E. Authorization ownership =================

    @Test
    void dealVisibilityRemainsTheDealServicesResponsibility() {
        call();

        // Customer360Service must not hold its own OWN/TEAM/ALL logic; it delegates.
        verify(dealService).listDeals(any(), isNull(), eq(accountId), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt());
    }

    @Test
    void entitlementVisibilityRemainsTheEntitlementServicesResponsibility() {
        call();

        verify(entitlementService).list(eq(tenantId), eq(accountId), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt());
    }

    @Test
    void ticketVisibilityRemainsTheTicketServicesResponsibility() {
        call();

        verify(ticketService).listTickets(eq(tenantId), eq(accountId), isNull(), isNull(),
                anyInt(), anyInt());
    }

    @Test
    void feedbackVisibilityRemainsTheFeedbackServicesResponsibility() {
        call();

        // Customer360Service must not hold its own OWN/TEAM/ALL logic for feedback
        // either; it delegates to FeedbackService, which applies feedback:read scope.
        var feedbackCaptor = org.mockito.ArgumentCaptor.forClass(FeedbackListParams.class);
        verify(feedbackService).list(eq(tenantId), feedbackCaptor.capture());
        assertThat(feedbackCaptor.getValue().getAccountId()).isEqualTo(accountId);
    }

    @Test
    void aFeedbackDenialStillFailsTheWholeRead() {
        // Strict like the ticket and entitlement sections: a caller with no feedback
        // scope fails the whole request rather than receiving a 360 that silently
        // omits one section. There is no partial-response contract upstream.
        when(feedbackService.list(any(), any()))
                .thenThrow(new PermissionDeniedException("SCOPE_DENIED", "no feedback:read scope"));

        assertThat(codeOf(catchThrowable(() -> call()))).isEqualTo("SCOPE_DENIED");
    }

    @Test
    void theFeedbackSectionReusesTheFeedbackResponseContract() {
        FeedbackResponse item = FeedbackResponse.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .type(com.shivang.crm.modules.feedback.entity.FeedbackType.CSAT)
                .score(4)
                .build();
        when(feedbackService.list(any(), any()))
                .thenReturn(pageOf(List.of(item), 0, 20, 1));

        Customer360Section<FeedbackResponse> feedback = call().getFeedback();

        // No Customer360FeedbackResponse copy: the section carries the domain DTO.
        assertThat(feedback.getItems()).containsExactly(item);
        assertThat(feedback.getTotal()).isEqualTo(1);
    }

    @Test
    void feedbackPaginationIsIndependentOfTheOtherSections() {
        service.getAccount360(accountId, tenantId, 0, 20, 0, 20, 0, 20, 0, 20, 5, 7, 0, 20);

        // Other sections stay on their own pages while feedback moves.
        verify(accountService).listContacts(accountId, tenantId, 0, 20);
        verify(ticketService).listTickets(any(), any(), any(), any(), eq(0), eq(20));
        var feedbackCaptor = org.mockito.ArgumentCaptor.forClass(FeedbackListParams.class);
        verify(feedbackService).list(any(), feedbackCaptor.capture());
        assertThat(feedbackCaptor.getValue().getPage()).isEqualTo(5);
        assertThat(feedbackCaptor.getValue().getSize()).isEqualTo(7);
    }

    @Test
    void aPerDomainDenialStillFailsTheWholeRead() {
        // P2 does not change this: a domain denial propagates rather than degrading to
        // a partial response, because no such contract exists upstream.
        when(dealService.listDeals(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt()))
                .thenThrow(new PermissionDeniedException("SCOPE_DENIED", "no deal:read scope"));

        assertThat(codeOf(catchThrowable(() -> call()))).isEqualTo("SCOPE_DENIED");
    }

    // ================= F. Shape =================

    @Test
    void theReadIsReadOnly() throws Exception {
        var annotation = Customer360Service.class
.getMethod("getAccount360", UUID.class, UUID.class,
                        int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isTrue();
    }

    @Test
    void theServiceExposesExactlyOneOperation() {
        assertThat(java.util.Arrays.stream(Customer360Service.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .map(java.lang.reflect.Method::getName))
                .containsExactly("getAccount360");
    }

    @Test
    void theServiceUsesNoRepositoryDirectly() {
        // Composition only: reaching into a repository would bypass its service's
        // authorization.
        assertThat(java.util.Arrays.stream(Customer360Service.class.getDeclaredFields())
                .map(f -> f.getType().getSimpleName()))
                .noneMatch(n -> n.endsWith("Repository"));
    }

    @Test
    void theSectionFactoryEchoesThePageRatherThanNormalisingIt() {
        Page<String> source = new PageImpl<>(
                List.of("a"), PageRequest.of(3, 5, Sort.by("x")), 42);

        Customer360Section<String> section = Customer360Section.of(source);

        assertThat(section.getPage()).isEqualTo(3);
        assertThat(section.getSize()).isEqualTo(5);
        assertThat(section.getTotal()).isEqualTo(42);
        assertThat(section.getTotalPages()).isEqualTo(9);
        assertThat(section.isHasNext()).isTrue();
    }

    @Test
    void theSectionExposesNoSpringPageInternals() {
        java.util.Set<String> fields = java.util.Arrays.stream(Customer360Section.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(java.lang.reflect.Field::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder(
                "items", "page", "size", "total", "totalPages", "hasNext");
        assertThat(fields).doesNotContain("pageable", "sort", "number", "content");
    }

}
