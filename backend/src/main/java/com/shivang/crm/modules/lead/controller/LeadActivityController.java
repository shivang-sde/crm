package com.shivang.crm.modules.lead.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/leads/{leadId}")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Lead Activities", description = "Lead Activities Management APIs")
public class LeadActivityController {

    private final ActivityService activityService;
    private final TenantContext tenantContext;

    @GetMapping("/activities")
    @Operation(summary = "Get lead activities", description = "Get all activities for a specific lead")
    public ResponseEntity<ApiResponse<java.util.List<ActivityResponse>>> getActivities(
            @Parameter(description = "Lead UUID")
            @PathVariable UUID leadId,

            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "50") int size) {

        log.info("GET /api/v1/leads/{}/activities - Getting activities", leadId);

        UUID tenantId = currentTenantId();
        Page<ActivityResponse> activities = activityService.getEntityActivities(leadId, "LEAD", tenantId, page, size);

        Map<String, Object> meta = Map.of(
            "page", activities.getNumber(),
            "size", activities.getSize(),
            "total", activities.getTotalElements(),
            "totalPages", activities.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(activities.getContent(), meta));
    }

    private UUID currentTenantId() {
        String tenantId = tenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Tenant context is not available");
        }
        return UUID.fromString(tenantId);
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("User authentication is not available");
        }
        return UUID.fromString(authentication.getPrincipal().toString());
    }
}
