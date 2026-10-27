package com.shivang.crm.modules.activity.controller;

import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.activity.service.ActivityAccessGuard;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Generic activity reads.
 *
 * <p>L3.1-P8: these endpoints previously filtered by tenant only, so any caller with
 * {@code activity:read} could read every activity row in the tenant — including the
 * audit trail of records their OWN or TEAM scope excludes. Reads are now authorized
 * through {@link ActivityAccessGuard}, which delegates to the parent entity's own
 * scope assertion, exactly as the entity-scoped activity endpoints already do.
 *
 * <p>This is a generic surface only. The Ticket timeline at
 * {@code GET /api/v1/tickets/{id}/activities} remains the canonical, narrower
 * boundary for Tickets.
 *
 * <p>There are no write endpoints: activities are produced by domain services and by
 * system producers such as the SLA scheduler, never by a client.
 */
@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;
    private final ActivityAccessGuard activityAccessGuard;
    private final TenantContext tenantContext;

    /**
     * Requires an explicit entity. An unfiltered tenant-wide listing spans entities the
     * caller may not read, and there is no per-parent authorization that can cover it.
     */
    @GetMapping
    @PreAuthorize("@rbac.has(authentication, 'activity', 'read')")
    public ResponseEntity<Page<ActivityResponse>> getUnifiedActivities(
        @RequestParam String entityType,
        @RequestParam UUID entityId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        UUID tenantId = tenantContext.getTenantId();
        activityAccessGuard.requireEntityReadable(tenantId, entityType, entityId, "read");

        return ResponseEntity.ok(
            activityService.getEntityActivities(entityId, entityType, tenantId, page, size));
    }

    @GetMapping("/{entityType}/{entityId}")
    @PreAuthorize("@rbac.has(authentication, 'activity', 'read')")
    public ResponseEntity<Page<ActivityResponse>> getEntityActivities(
        @PathVariable String entityType,
        @PathVariable UUID entityId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        UUID tenantId = tenantContext.getTenantId();
        activityAccessGuard.requireEntityReadable(tenantId, entityType, entityId, "read");

        return ResponseEntity.ok(
            activityService.getEntityActivities(entityId, entityType, tenantId, page, size));
    }

    @GetMapping("/{entityType}/{entityId}/recent")
    @PreAuthorize("@rbac.has(authentication, 'activity', 'read')")
    public ResponseEntity<List<ActivityResponse>> getRecentActivities(
        @PathVariable String entityType,
        @PathVariable UUID entityId,
        @RequestParam(required = false) List<String> activityTypes
    ) {
        UUID tenantId = tenantContext.getTenantId();
        activityAccessGuard.requireEntityReadable(tenantId, entityType, entityId, "read");

        if (activityTypes == null || activityTypes.isEmpty()) {
            activityTypes = List.of("TASK", "CALL", "MEETING", "NOTE", "EMAIL");
        }

        return ResponseEntity.ok(
            activityService.getRecentActivities(entityId, entityType, tenantId, activityTypes));
    }
}
