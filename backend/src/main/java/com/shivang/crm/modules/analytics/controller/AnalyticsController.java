package com.shivang.crm.modules.analytics.controller;

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
                    + "(PLATFORM, RESELLER, TENANT or USER).")
    public ResponseEntity<ApiResponse<AnalyticsSummaryResponse>> getSummary(
            @Parameter(description = "Optional requested scope downgrade: PLATFORM, RESELLER, TENANT or USER")
            @RequestParam(name = "scope", required = false) String scope,
            @Parameter(description = "Range start, ISO-8601 instant (default: to minus 30 days)")
            @RequestParam(name = "from", required = false) String from,
            @Parameter(description = "Range end (exclusive), ISO-8601 instant (default: now)")
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/summary - scope={}, from={}, to={}", scope, from, to);

        AnalyticsContext context = scopeResolver.resolve(scope);
        AnalyticsDateRange range = AnalyticsDateRange.resolve(from, to);
        AnalyticsSummaryResponse response = analyticsService.getSummary(context, range);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
