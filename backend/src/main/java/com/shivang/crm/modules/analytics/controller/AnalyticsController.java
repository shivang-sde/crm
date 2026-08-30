package com.shivang.crm.modules.analytics.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.analytics.AnalyticsContext;
import com.shivang.crm.modules.analytics.AnalyticsDateRange;
import com.shivang.crm.modules.analytics.AnalyticsScopeResolver;
import com.shivang.crm.modules.analytics.dto.AnalyticsSummaryResponse;
import com.shivang.crm.modules.analytics.dto.AnalyticsTrendResponse;
import com.shivang.crm.modules.analytics.service.AnalyticsService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Scoped aggregate analytics for dashboards")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AnalyticsScopeResolver scopeResolver;

    @GetMapping("/summary")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Get scoped aggregate summary",
            description = "Returns lead/contact/deal/task/call/meeting counts for the "
                    + "analytics scope derived from the authenticated user "
                    + "(PLATFORM, RESELLER, TENANT or USER). "
                    + "Optional tenantId allows SUPERADMIN/RESELLER to drill into a specific tenant.")
    public ResponseEntity<ApiResponse<AnalyticsSummaryResponse>> getSummary(
            @Parameter(description = "Optional requested scope downgrade: PLATFORM, RESELLER, TENANT or USER")
            @RequestParam(name = "scope", required = false) String scope,
            @Parameter(description = "Optional tenant UUID to drill into (SUPERADMIN/RESELLER only)")
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @Parameter(description = "Range start, ISO-8601 instant (default: to minus 30 days)")
            @RequestParam(name = "from", required = false) String from,
            @Parameter(description = "Range end (exclusive), ISO-8601 instant (default: now)")
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/summary - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        AnalyticsContext context = scopeResolver.resolve(scope, tenantId);
        AnalyticsDateRange range = AnalyticsDateRange.resolve(from, to);
        AnalyticsSummaryResponse response = analyticsService.getSummary(context, range);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/trends")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Get time-bucketed trend data",
            description = "Returns lead/contact/deal/task counts per time bucket "
                    + "(DAY, WEEK or MONTH auto-selected based on range). "
                    + "Optional tenantId allows SUPERADMIN/RESELLER to drill into a specific tenant.")
    public ResponseEntity<ApiResponse<List<AnalyticsTrendResponse>>> getTrends(
            @Parameter(description = "Optional requested scope downgrade")
            @RequestParam(name = "scope", required = false) String scope,
            @Parameter(description = "Optional tenant UUID to drill into (SUPERADMIN/RESELLER only)")
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @Parameter(description = "Range start, ISO-8601 instant (default: to minus 30 days)")
            @RequestParam(name = "from", required = false) String from,
            @Parameter(description = "Range end (exclusive), ISO-8601 instant (default: now)")
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/trends - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        AnalyticsContext context = scopeResolver.resolve(scope, tenantId);
        AnalyticsDateRange range = AnalyticsDateRange.resolve(from, to);
        List<AnalyticsTrendResponse> response = analyticsService.getTrends(context, range);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
