package com.shivang.crm.modules.activity.controller;

import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;
    private final TenantContext tenantContext;

    @GetMapping
    public ResponseEntity<Page<ActivityResponse>> getUnifiedActivities(
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) UUID entityId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        UUID tenantId = tenantContext.getTenantId();
        
        if (entityType == null || entityId == null) {
            // Return all activities for the tenant (may need pagination optimization)
            return ResponseEntity.ok(activityService.getEntityActivities(null, null, tenantId, page, size));
        }
        
        Page<ActivityResponse> activities = activityService.getEntityActivities(entityId, entityType, tenantId, page, size);
        return ResponseEntity.ok(activities);
    }

    @GetMapping("/{entityType}/{entityId}")
    public ResponseEntity<Page<ActivityResponse>> getEntityActivities(
        @PathVariable String entityType,
        @PathVariable UUID entityId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        UUID tenantId = tenantContext.getTenantId();
        Page<ActivityResponse> activities = activityService.getEntityActivities(entityId, entityType, tenantId, page, size);
        return ResponseEntity.ok(activities);
    }

    @GetMapping("/{entityType}/{entityId}/recent")
    public ResponseEntity<List<ActivityResponse>> getRecentActivities(
        @PathVariable String entityType,
        @PathVariable UUID entityId,
        @RequestParam(required = false) List<String> activityTypes
    ) {
        UUID tenantId = tenantContext.getTenantId();
        
        if (activityTypes == null || activityTypes.isEmpty()) {
            activityTypes = List.of("TASK", "CALL", "MEETING", "NOTE", "EMAIL");
        }
        
        List<ActivityResponse> activities = activityService.getRecentActivities(entityId, entityType, tenantId, activityTypes);
        return ResponseEntity.ok(activities);
    }
}
