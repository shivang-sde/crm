package com.shivang.crm.modules.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shivang.crm.modules.account.repository.Customer360TimelineQueryRepository;
import com.shivang.crm.modules.account.repository.Customer360TimelineQueryRepository.TimelineVisibility;
import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.activity.entity.Activity;
import com.shivang.crm.modules.activity.mapper.ActivityMapper;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.exception.PermissionDeniedException;

/**
 * L3.2-P3: scope resolution for the Account timeline.
 *
 * <p>The property under test is that the timeline inherits each domain's own read
 * scope rather than inventing one. A timeline that resolved a single global scope
 * would either over-expose (one permissive scope for four domains) or
 * under-expose (one restrictive scope for four), and neither would match the
 * sections rendered in the same response.
 */
class Customer360TimelineServiceTest {

    private Customer360TimelineQueryRepository timelineQueryRepository;
    private ActivityMapper activityMapper;
    private PermissionEvaluatorService permissionEvaluatorService;
    private RecordScopeGuard recordScopeGuard;
    private Customer360TimelineService service;

    private UUID tenantId;
    private UUID accountId;
    private UUID callerId;

    @BeforeEach
    void setup() {
        timelineQueryRepository = mock(Customer360TimelineQueryRepository.class);
        activityMapper = mock(ActivityMapper.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        recordScopeGuard = mock(RecordScopeGuard.class);

        service = new Customer360TimelineService(
                timelineQueryRepository, activityMapper, permissionEvaluatorService, recordScopeGuard);

        tenantId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        // ALL for everything: the permissive default that lets unrelated assertions
        // focus on the scope plumbing rather than on scope values.
        when(permissionEvaluatorService.getAccessScope(any(), eq(tenantId), any(), eq("read")))
                .thenReturn("ALL");
        when(recordScopeGuard.requireScope(eq(tenantId), any(), any(), eq("read"))).thenReturn("ALL");
        when(timelineQueryRepository.findTimelineForAccount(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        callerId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(callerId.toString(), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private TimelineVisibility capturedVisibility() {
        org.mockito.ArgumentCaptor<TimelineVisibility> captor =
                org.mockito.ArgumentCaptor.forClass(TimelineVisibility.class);
        verify(timelineQueryRepository).findTimelineForAccount(any(), any(), captor.capture(), any());
        return captor.getValue();
    }

    // ================= A. Account is the caller's responsibility, not ours =================

    @Test
    void theTimelineDoesNotReAssertAccountAccess() {
        // Customer360Service has already asserted account:read before calling this. A
        // second assertion would be redundant, and repeating it here would hide an
        // ordering bug in the orchestrator.
        service.getAccountTimeline(tenantId, accountId, 0, 20);

        verify(recordScopeGuard, never()).requireScope(eq(tenantId), any(), eq("account"), eq("read"));
        verify(recordScopeGuard, never()).withinOwnerCreatorScope(any(), any(), any(), any(), any());
    }

    @Test
    void theAccountIsForwardedToTheQuery() {
        service.getAccountTimeline(tenantId, accountId, 0, 20);

        verify(timelineQueryRepository).findTimelineForAccount(eq(tenantId), eq(accountId), any(), any());
    }

    // ================= B. Per-domain scope resolution =================

    @Test
    void eachDomainScopeIsResolvedForItsOwnModule() {
        service.getAccountTimeline(tenantId, accountId, 0, 20);

        // contact and deal use the lenient evaluator, matching their list endpoints.
        verify(permissionEvaluatorService).getAccessScope(any(), eq(tenantId), eq("contact"), eq("read"));
        verify(permissionEvaluatorService).getAccessScope(any(), eq(tenantId), eq("deal"), eq("read"));
        // ticket, entitlement and feedback use the strict guard, matching theirs too.
        verify(recordScopeGuard).requireScope(eq(tenantId), any(), eq("ticket"), eq("read"));
        verify(recordScopeGuard).requireScope(eq(tenantId), any(), eq("entitlement"), eq("read"));
        verify(recordScopeGuard).requireScope(eq(tenantId), any(), eq("feedback"), eq("read"));
    }

    @Test
    void theTimelineNeverResolvesAWholeAccountScope() {
        // There is no customer360 module in this design. If the timeline ever asked for
        // an "account" scope to filter children, it would be reusing the parent's
        // permission to widen the children.
        service.getAccountTimeline(tenantId, accountId, 0, 20);

        verify(permissionEvaluatorService, never())
                .getAccessScope(any(), any(), eq("customer360"), any());
        verify(recordScopeGuard, never()).requireScope(any(), any(), eq("customer360"), any());
    }

    @Test
    void eachDomainKeepsItsOwnScopeValue() {
        when(permissionEvaluatorService.getAccessScope(any(), eq(tenantId), eq("contact"), eq("read")))
                .thenReturn("OWN");
        when(permissionEvaluatorService.getAccessScope(any(), eq(tenantId), eq("deal"), eq("read")))
                .thenReturn("TEAM");
        when(recordScopeGuard.requireScope(eq(tenantId), any(), eq("ticket"), eq("read")))
                .thenReturn("ALL");
        when(recordScopeGuard.requireScope(eq(tenantId), any(), eq("entitlement"), eq("read")))
                .thenReturn("OWN");
        when(recordScopeGuard.requireScope(eq(tenantId), any(), eq("feedback"), eq("read")))
                .thenReturn("TEAM");

        service.getAccountTimeline(tenantId, accountId, 0, 20);

        TimelineVisibility visibility = capturedVisibility();

        assertThat(visibility.contactScope()).isEqualTo("OWN");
        assertThat(visibility.dealScope()).isEqualTo("TEAM");
        assertThat(visibility.ticketScope()).isEqualTo("ALL");
        assertThat(visibility.entitlementScope()).isEqualTo("OWN");
        assertThat(visibility.feedbackScope()).isEqualTo("TEAM");
    }
    @Test
    void aNarrowContactScopeDoesNotNarrowTheTimelineAsAWhole() {
        // The account is readable and the caller may see no contacts; the timeline must
        // still return the account's, tickets' and deals' activities.
        when(permissionEvaluatorService.getAccessScope(any(), eq(tenantId), eq("contact"), eq("read")))
                .thenReturn("OWN");

        service.getAccountTimeline(tenantId, accountId, 0, 20);

        TimelineVisibility visibility = capturedVisibility();
        assertThat(visibility.contactScope()).isEqualTo("OWN");
        assertThat(visibility.ticketScope()).isEqualTo("ALL");
    }

    // ================= C. Denial propagation =================

    @Test
    void aMissingTicketScopeStillFailsTheRequest() {
        // Matches TicketService.listTickets, which the sibling section already calls:
        // the timeline must not be more permissive than the section above it.
        when(recordScopeGuard.requireScope(eq(tenantId), any(), eq("ticket"), eq("read")))
                .thenThrow(new PermissionDeniedException("SCOPE_DENIED", "no ticket scope"));

        assertThat(catchThrowable(() -> service.getAccountTimeline(tenantId, accountId, 0, 20)))
                .isInstanceOf(PermissionDeniedException.class);

        verifyNoInteractions(timelineQueryRepository);
    }

    @Test
    void aMissingEntitlementScopeStillFailsTheRequest() {
        when(recordScopeGuard.requireScope(eq(tenantId), any(), eq("entitlement"), eq("read")))
                .thenThrow(new PermissionDeniedException("SCOPE_DENIED", "no entitlement scope"));

        assertThat(catchThrowable(() -> service.getAccountTimeline(tenantId, accountId, 0, 20)))
                .isInstanceOf(PermissionDeniedException.class);

        verifyNoInteractions(timelineQueryRepository);
    }

    @Test
    void aMissingFeedbackScopeStillFailsTheRequest() {
        // Matches FeedbackService.list, which the feedback section calls: the timeline
        // must not be more permissive than the section rendered alongside it.
        when(recordScopeGuard.requireScope(eq(tenantId), any(), eq("feedback"), eq("read")))
                .thenThrow(new PermissionDeniedException("SCOPE_DENIED", "no feedback scope"));

        assertThat(catchThrowable(() -> service.getAccountTimeline(tenantId, accountId, 0, 20)))
                .isInstanceOf(PermissionDeniedException.class);

        verifyNoInteractions(timelineQueryRepository);
    }

    @Test
    void anAbsentContactOrDealScopeYieldsNoRowsRatherThanFailing() {
        // Matches AccountService.listContacts and DealService.listDeals, which pass the
        // raw scope through and let visibleToUser fail closed.
        when(permissionEvaluatorService.getAccessScope(any(), eq(tenantId), eq("contact"), eq("read")))
                .thenReturn("NONE");
        when(permissionEvaluatorService.getAccessScope(any(), eq(tenantId), eq("deal"), eq("read")))
                .thenReturn("NONE");

        service.getAccountTimeline(tenantId, accountId, 0, 20);

        // "NONE" reaches the query, where visibleToUser's default branch matches no rows.
        assertThat(capturedVisibility().contactScope()).isEqualTo("NONE");
        assertThat(capturedVisibility().dealScope()).isEqualTo("NONE");
    }

    // ================= D. Team membership =================

    @Test
    void teamMembershipIsResolvedOnceForAllDomains() {
        // teamMemberIds delegates to the same getTeamUserIds query that contact and deal
        // use directly, so one lookup covers four domains.
        when(permissionEvaluatorService.getAccessScope(any(), eq(tenantId), eq("deal"), eq("read")))
                .thenReturn("TEAM");
        when(recordScopeGuard.teamMemberIds(eq(tenantId), any())).thenReturn(List.of());

        service.getAccountTimeline(tenantId, accountId, 0, 20);

        verify(recordScopeGuard, org.mockito.Mockito.times(1)).teamMemberIds(eq(tenantId), any());
    }

    @Test
    void teamMembershipIsNotLookedUpWhenNoScopeNeedsIt() {
        service.getAccountTimeline(tenantId, accountId, 0, 20);

        verify(recordScopeGuard, never()).teamMemberIds(any(), any());
        assertThat(capturedVisibility().teamUserIds()).isEmpty();
    }

    @Test
    void theCallerIsIncludedInTeamMembership() {
        // A manager with no direct reports would otherwise produce an empty `IN ()`
        // clause. Under TEAM the specifications already OR in ownerId = caller, so
        // adding the caller changes no result.
        when(recordScopeGuard.requireScope(eq(tenantId), any(), eq("ticket"), eq("read")))
                .thenReturn("TEAM");
        when(recordScopeGuard.teamMemberIds(eq(tenantId), any())).thenReturn(List.of());

        service.getAccountTimeline(tenantId, accountId, 0, 20);

        assertThat(capturedVisibility().teamUserIds()).isNotEmpty();
    }

    @Test
    void teamMembershipCarriesBothReportsAndTheCaller() {
        when(recordScopeGuard.requireScope(eq(tenantId), any(), eq("ticket"), eq("read")))
                .thenReturn("TEAM");
        UUID report = UUID.randomUUID();
        when(recordScopeGuard.teamMemberIds(eq(tenantId), any())).thenReturn(List.of(report));

        service.getAccountTimeline(tenantId, accountId, 0, 20);

        assertThat(capturedVisibility().teamUserIds()).hasSize(2).contains(report);
    }

    // ================= E. Pagination and mapping =================

    @Test
    void theRequestedPageReachesTheQuery() {
        service.getAccountTimeline(tenantId, accountId, 4, 15);

        verify(timelineQueryRepository).findTimelineForAccount(
                eq(tenantId), eq(accountId), any(), eq(PageRequest.of(4, 15)));
    }

    @Test
    void paginationIsNotClampedTwice() {
        // Clamping belongs to Customer360Service, which owns the endpoint contract.
        // This service forwards what it is given rather than silently re-bounding it.
        service.getAccountTimeline(tenantId, accountId, 2, 500);

        verify(timelineQueryRepository).findTimelineForAccount(
                any(), any(), any(), eq(PageRequest.of(2, 500)));
    }

    @Test
    void activitiesAreMappedToTheExistingResponseType() {
        ActivityResponse expected = ActivityResponse.builder().activityType("DEAL_CREATED").build();
        Activity entity = mock(Activity.class);
        when(activityMapper.toResponse(entity)).thenReturn(expected);
        when(timelineQueryRepository.findTimelineForAccount(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1));

        Page<ActivityResponse> page = service.getAccountTimeline(tenantId, accountId, 0, 20);

        // No timeline-specific DTO: ActivityResponse is reused as-is.
        assertThat(page.getContent()).containsExactly(expected);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void metadataIsNotRebuilt() {
        when(timelineQueryRepository.findTimelineForAccount(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(3, 20), 137));

        Page<ActivityResponse> page = service.getAccountTimeline(tenantId, accountId, 3, 20);

        // total and totalPages come straight from the query's count, so the timeline
        // cannot report a different total than the one it paged against.
        assertThat(page.getNumber()).isEqualTo(3);
        assertThat(page.getSize()).isEqualTo(20);
        assertThat(page.getTotalElements()).isEqualTo(137);
        assertThat(page.getTotalPages()).isEqualTo(7);
    }

    // ================= F. Shape =================

    @Test
    void theReadIsReadOnly() throws Exception {
        var annotation = Customer360TimelineService.class
                .getMethod("getAccountTimeline", UUID.class, UUID.class, int.class, int.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isTrue();
    }

    @Test
    void theServiceExposesExactlyOneOperation() {
        assertThat(java.util.Arrays.stream(Customer360TimelineService.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .map(java.lang.reflect.Method::getName))
                .containsExactly("getAccountTimeline");
    }

    @Test
    void theServiceTouchesNoOtherDomainRepository() {
        // It resolves scopes and delegates. Only the timeline query repository is allowed,
        // so this service cannot quietly bypass a domain's own list endpoint.
        assertThat(java.util.Arrays.stream(Customer360TimelineService.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(f -> f.getType().getSimpleName()))
                .containsExactlyInAnyOrder(
                        "Customer360TimelineQueryRepository",
                        "ActivityMapper",
                        "PermissionEvaluatorService",
                        "RecordScopeGuard");
    }

    @Test
    void theGenericActivityListingIsNotUsed() {
        service.getAccountTimeline(tenantId, accountId, 0, 20);

        // ActivityService.getEntityActivities is single-entity and unauthorized; the
        // tenant-wide finder it does expose has a different permission contract.
        verify(activityMapper, never()).toResponseList(any());
    }
}
