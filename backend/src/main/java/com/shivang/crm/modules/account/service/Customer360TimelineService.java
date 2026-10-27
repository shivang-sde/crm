package com.shivang.crm.modules.account.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.account.repository.Customer360TimelineQueryRepository;
import com.shivang.crm.modules.account.repository.Customer360TimelineQueryRepository.TimelineVisibility;
import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.activity.entity.Activity;
import com.shivang.crm.modules.activity.mapper.ActivityMapper;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.util.UserUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.2-P3: the Account's unified customer timeline.
 *
 * <p>Kept separate from {@link Customer360Service} so the orchestration boundary stays
 * readable: that class coordinates sections, this class owns the timeline's one
 * concern — turning four separate read scopes into one database query.
 *
 * <p>Account access has already been asserted by the caller before this runs, so this
 * service does not repeat it. What it does do is resolve the same per-domain read
 * scopes that the four child sections resolve, and hand them to the query so a record
 * the caller cannot see cannot contribute its activities.
 *
 * <p>Scope resolution deliberately mirrors each domain's own service rather than
 * imposing one rule:
 * <ul>
 *   <li>contact / deal use {@code getAccessScope}, so a domain with no read scope
 *       yields an empty section rather than failing the whole request;</li>
 *   <li>ticket / entitlement / feedback use {@code requireScope}, which throws —
 *       matching {@code TicketService.listTickets},
 *       {@code CustomerEntitlementService.list} and {@code FeedbackService.list},
 *       which the sibling sections call.</li>
 * </ul>
 * A timeline therefore never authorizes differently from the sections rendered
 * alongside it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Customer360TimelineService {

    private final Customer360TimelineQueryRepository timelineQueryRepository;
    private final ActivityMapper activityMapper;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final RecordScopeGuard recordScopeGuard;

    /**
     * Newest-first page of activities on this Account's visible records.
     *
     * <p>{@code page} and {@code size} are expected to be already normalized by
     * {@link Customer360Service}, which owns the endpoint's parameter contract.
     */
    @Transactional(readOnly = true)
    public Page<ActivityResponse> getAccountTimeline(
            UUID tenantId,
            UUID accountId,
            int page,
            int size) {

        UUID userId = UserUtil.currentUserId();

        String contactScope = permissionEvaluatorService.getAccessScope(userId, tenantId, "contact", "read");
        String dealScope = permissionEvaluatorService.getAccessScope(userId, tenantId, "deal", "read");
        // Throws SCOPE_DENIED when the caller has no record scope at all, exactly as the
        // ticket and entitlement sections already do for this same request.
        String ticketScope = recordScopeGuard.requireScope(tenantId, userId, "ticket", "read");
        String entitlementScope = recordScopeGuard.requireScope(tenantId, userId, "entitlement", "read");
        // L3.3-P3: strict like the ticket and entitlement scopes, matching
        // FeedbackService.list, which the feedback section calls.
        String feedbackScope = recordScopeGuard.requireScope(tenantId, userId, "feedback", "read");

        List<UUID> teamUserIds = teamUserIdsIfNeeded(
                tenantId, userId, contactScope, dealScope, ticketScope, entitlementScope, feedbackScope);

        Page<Activity> activities = timelineQueryRepository.findTimelineForAccount(
                tenantId,
                accountId,
                new TimelineVisibility(userId, contactScope, dealScope, ticketScope,
                        entitlementScope, feedbackScope, teamUserIds),
                PageRequest.of(page, size));

        log.debug("Customer 360 timeline for account {}: page {}/{} returned {} of {} visible activities",
                accountId, page, size, activities.getNumberOfElements(), activities.getTotalElements());

        return activities.map(activityMapper::toResponse);
    }

    /**
     * Team membership for the {@code TEAM} branch of the visibility specifications.
     *
     * <p>One lookup covers all four domains: {@code teamMemberIds} delegates to the
     * same {@code getTeamUserIds} query that contact and deal use directly, so calling
     * it per domain would repeat an identical database read.
     *
     * <p>The caller is folded into the list. Under {@code TEAM} the specifications
     * already OR in {@code ownerId = userId}, so adding the caller is semantically a
     * no-op — but it guarantees the {@code ownerId IN (...)} clause is never empty,
     * which a manager with no direct reports would otherwise produce.
     */
    private List<UUID> teamUserIdsIfNeeded(
            UUID tenantId,
            UUID userId,
            String... scopes) {

        boolean anyTeamScope = Arrays.stream(scopes).anyMatch("TEAM"::equals);
        if (!anyTeamScope) {
            return List.of();
        }

        Set<UUID> visibleOwners = new LinkedHashSet<>(recordScopeGuard.teamMemberIds(tenantId, userId));
        visibleOwners.add(userId);
        return List.copyOf(visibleOwners);
    }
}
