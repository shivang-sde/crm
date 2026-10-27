package com.shivang.crm.modules.ticket.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.activity.mapper.ActivityMapper;
import com.shivang.crm.modules.activity.repository.ActivityRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.1-P5: reads a Ticket's timeline.
 *
 * <p>Read-only by construction. Ticket activities are written by the domain service
 * that owns each mutation, inside that mutation's transaction; nothing here writes,
 * so a read can never add a timeline entry.
 *
 * <p>The generic {@code /api/v1/activities} surface cannot serve this: it takes an
 * entity id from the path and filters only by tenant, so it would hand a Ticket's
 * timeline to a caller whose OWN or TEAM scope excludes that Ticket. This service
 * resolves the parent's record scope first and only then queries activities, so the
 * timeline never widens Ticket access.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketTimelineService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final String TICKET_ENTITY_TYPE = "TICKET";

    private final ActivityRepository activityRepository;
    private final ActivityMapper activityMapper;
    private final TicketService ticketService;

    @Transactional(readOnly = true)
    public Page<ActivityResponse> getTicketTimeline(
            UUID ticketId, UUID tenantId, UUID userId, int page, int size) {

        // Throws TICKET_NOT_FOUND for a missing, deleted or cross-tenant Ticket and
        // SCOPE_DENIED for one outside the caller's scope. The activity query is only
        // reached once the caller has proven Ticket access.
        ticketService.requireReadable(ticketId, tenantId, userId);

        return activityRepository
                .findByEntityTypeAndEntityIdAndTenantIdAndDeletedFalseOrderByCreatedAtDesc(
                        TICKET_ENTITY_TYPE, ticketId, tenantId, pageable(page, size))
                .map(activityMapper::toResponse);
    }

    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size == 0 ? DEFAULT_PAGE_SIZE : size));
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
