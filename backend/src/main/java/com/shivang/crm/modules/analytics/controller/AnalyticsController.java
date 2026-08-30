package com.shivang.crm.modules.analytics.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
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
import com.shivang.crm.modules.analytics.service.AnalyticsExportService;
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
    private final AnalyticsExportService analyticsExportService;

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

    @GetMapping("/export/summary")
    @PreAuthorize("@rbac.has(authentication, 'report', 'export')")
    @Operation(summary = "Export scoped aggregate summary as CSV",
            description = "Returns the same scoped summary data as GET /summary as a "
                    + "CSV attachment. Scope, tenantId, from and to behave identically.")
    public ResponseEntity<byte[]> exportSummary(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/export/summary - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        AnalyticsContext context = scopeResolver.resolve(scope, tenantId);
        AnalyticsDateRange range = AnalyticsDateRange.resolve(from, to);
        return csvResponse("summary", context, range, analyticsExportService.summaryCsv(context, range));
    }

    @GetMapping("/export/trends")
    @PreAuthorize("@rbac.has(authentication, 'report', 'export')")
    @Operation(summary = "Export trend data as CSV",
            description = "Returns the same trend data as GET /trends as a CSV "
                    + "attachment. Scope, tenantId, from and to behave identically.")
    public ResponseEntity<byte[]> exportTrends(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/export/trends - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        AnalyticsContext context = scopeResolver.resolve(scope, tenantId);
        AnalyticsDateRange range = AnalyticsDateRange.resolve(from, to);
        return csvResponse("trends", context, range, analyticsExportService.trendsCsv(context, range));
    }

    private ResponseEntity<byte[]> csvResponse(
            String kind, AnalyticsContext context, AnalyticsDateRange range, String csv) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + analyticsExportService.fileName(kind, context, range) + "\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
