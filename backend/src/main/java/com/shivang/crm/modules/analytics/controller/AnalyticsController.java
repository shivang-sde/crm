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
import com.shivang.crm.modules.analytics.dto.AccountsByOwnerRow;
import com.shivang.crm.modules.analytics.dto.ActivityRatesSummary;
import com.shivang.crm.modules.analytics.dto.CallDurationSummary;
import com.shivang.crm.modules.analytics.dto.CallStatusSummary;
import com.shivang.crm.modules.analytics.dto.ContactsPerAccountRow;
import com.shivang.crm.modules.analytics.dto.ConversionOwnerRow;
import com.shivang.crm.modules.analytics.dto.ConversionPeriodSummary;
import com.shivang.crm.modules.analytics.dto.CurrentStageAgeSummary;
import com.shivang.crm.modules.analytics.dto.DealAgingRow;
import com.shivang.crm.modules.analytics.dto.ForecastCategoryRow;
import com.shivang.crm.modules.analytics.dto.LeadSourcePerformanceRow;
import com.shivang.crm.modules.analytics.dto.PipelineAccountRow;
import com.shivang.crm.modules.analytics.dto.PipelineOwnerRow;
import com.shivang.crm.modules.analytics.dto.PipelineStageRow;
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

        Resolved r = resolve(scope, tenantId, from, to);
        return csvResponse("trends", r.context(), r.range(), analyticsExportService.trendsCsv(r.context(), r.range()));
    }

    @GetMapping("/pipeline/stage")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Pipeline aggregate grouped by current deal stage",
            description = "Deals created in the selected period, grouped by their current stage. "
                    + "Same scope/date semantics as GET /summary.")
    public ResponseEntity<ApiResponse<List<PipelineStageRow>>> getPipelineByStage(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/pipeline/stage - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getPipelineByStage(r.context(), r.range())));
    }

    @GetMapping("/pipeline/owner")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Pipeline aggregate grouped by owner",
            description = "Deals created in the selected period, grouped by owner, derived only from "
                    + "the caller's already-authorized analytics scope. Same scope/date semantics as GET /summary.")
    public ResponseEntity<ApiResponse<List<PipelineOwnerRow>>> getPipelineByOwner(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/pipeline/owner - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getPipelineByOwner(r.context(), r.range())));
    }

    @GetMapping("/pipeline/account")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Pipeline aggregate grouped by account",
            description = "Deals created in the selected period, grouped by account. Accounts only appear "
                    + "when they own qualifying, in-scope deals. Same scope/date semantics as GET /summary.")
    public ResponseEntity<ApiResponse<List<PipelineAccountRow>>> getPipelineByAccount(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/pipeline/account - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getPipelineByAccount(r.context(), r.range())));
    }

    @GetMapping("/conversion/owner")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Lead conversion aggregate grouped by owner",
            description = "Leads created in the selected period, grouped by owner, with AN-10.1 "
                    + "created-window conversion semantics. Same scope/date semantics as GET /summary.")
    public ResponseEntity<ApiResponse<List<ConversionOwnerRow>>> getConversionByOwner(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/conversion/owner - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getConversionByOwner(r.context(), r.range())));
    }

    @GetMapping("/deals/aging")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Open deal aging buckets",
            description = "Open deals created in the selected period bucketed by age "
                    + "(0-7, 8-30, 31-60, 61-90, 90+ days) measured at request time. "
                    + "Same scope/date semantics as GET /summary.")
    public ResponseEntity<ApiResponse<List<DealAgingRow>>> getDealAging(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/deals/aging - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getDealAging(r.context(), r.range())));
    }

    @GetMapping("/calls/status")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Call status summary",
            description = "PLANNED/HELD/NOT_HELD/CANCELLED counts for calls created in the selected "
                    + "period, with heldRate = held / (held + notHeld + cancelled). "
                    + "Same scope/date semantics as GET /summary.")
    public ResponseEntity<ApiResponse<CallStatusSummary>> getCallStatus(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/calls/status - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getCallStatus(r.context(), r.range())));
    }

    @GetMapping("/conversion/period")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Lead conversion during the selected period",
            description = "Counts leads whose convertedAt falls in the selected [from, to) window "
                    + "and inside the caller's resolved analytics scope. Conversion-EVENT semantics, "
                    + "distinct from the created-window conversion/owner metric.")
    public ResponseEntity<ApiResponse<ConversionPeriodSummary>> getConversionDuringPeriod(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/conversion/period - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getConversionDuringPeriod(r.context(), r.range())));
    }

    @GetMapping("/pipeline/forecast-category")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Pipeline breakout by forecast category",
            description = "Deals created in the selected period grouped by their current, persisted "
                    + "forecast_category. Pure current-data breakout - no forecasting math. Same scope/date "
                    + "semantics as GET /summary.")
    public ResponseEntity<ApiResponse<List<ForecastCategoryRow>>> getPipelineByForecastCategory(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/pipeline/forecast-category - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getPipelineByForecastCategory(r.context(), r.range())));
    }

    @GetMapping("/deals/current-stage-age")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Open deal average age and time in current stage",
            description = "Average deal age (created_at basis) and average time in current stage "
                    + "(stage_entered_at basis) for open deals created in the selected period. "
                    + "Current-snapshot semantics. Same scope/date semantics as GET /summary.")
    public ResponseEntity<ApiResponse<CurrentStageAgeSummary>> getCurrentStageAge(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/deals/current-stage-age - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getCurrentStageAge(r.context(), r.range())));
    }

    @GetMapping("/activity/rates")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Task completion/overdue rates and meeting status",
            description = "Task completion rate, task overdue rate, and meeting status distribution "
                    + "(PLANNED/HELD/NOT_HELD/CANCELLED + held rate) for the selected period, inside the "
                    + "caller's resolved analytics scope. Denominators are documented and bounded to [0,100].")
    public ResponseEntity<ApiResponse<ActivityRatesSummary>> getActivityRates(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/activity/rates - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getActivityRates(r.context(), r.range())));
    }

    @GetMapping("/calls/duration")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Call duration metrics",
            description = "Call duration metrics using ONLY authoritative durationMinutes (AN-14/CDR) "
                    + "for calls created in the selected period. Calls without captured duration are "
                    + "reported separately, never treated as zero. Same scope/date semantics as GET /summary.")
    public ResponseEntity<ApiResponse<CallDurationSummary>> getCallDuration(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/calls/duration - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getCallDuration(r.context(), r.range())));
    }

    @GetMapping("/leads/source")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Lead source performance",
            description = "Leads created in the selected period grouped by their authoritative LeadSource, "
                    + "with created-window cohort conversion (convertedCount = those same leads that have "
                    + "since converted). Leads with no source are a first-class UNSPECIFIED bucket so the "
                    + "summed leadCount reconciles with summary.leads. Same scope/date semantics as GET /summary.")
    public ResponseEntity<ApiResponse<List<LeadSourcePerformanceRow>>> getLeadSourcePerformance(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/leads/source - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getLeadSourcePerformance(r.context(), r.range())));
    }

    @GetMapping("/contacts/account")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Contacts per account",
            description = "Contacts created in the selected period grouped by their own authorized account_id. "
                    + "The group dimension is derived only from the already-scoped contact set - account id is "
                    + "never accepted as a filter. Contacts with no account are a first-class NO ACCOUNT bucket "
                    + "so the summed contactCount reconciles with summary.contacts. Same scope/date semantics as "
                    + "GET /summary.")
    public ResponseEntity<ApiResponse<List<ContactsPerAccountRow>>> getContactsPerAccount(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/contacts/account - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getContactsPerAccount(r.context(), r.range())));
    }

    @GetMapping("/accounts/owner")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Accounts grouped by owner",
            description = "Accounts created in the selected period grouped by their owning user, derived only "
                    + "from each account inside the caller's already-authorized account scope. Owner id is never "
                    + "accepted as a filter. Accounts with no owner are a first-class UNASSIGNED bucket so the "
                    + "summed accountCount reconciles with the authorized account population. Same scope/date "
                    + "semantics as GET /summary.")
    public ResponseEntity<ApiResponse<List<AccountsByOwnerRow>>> getAccountsByOwner(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/accounts/owner - scope={}, tenantId={}, from={}, to={}", scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getAccountsByOwner(r.context(), r.range())));
    }

    @GetMapping("/export/grouped")
    @PreAuthorize("@rbac.has(authentication, 'report', 'export')")
    @Operation(summary = "Export a grouped analytics dataset as CSV",
            description = "Downloads one grouped dataset (pipeline-stage, pipeline-owner, pipeline-account, "
                    + "conversion-owner, deals-aging, calls-status, conversion-period, forecast-category, "
                    + "current-stage-age, activity-rates, calls-duration, lead-source, contacts-account, "
                    + "accounts-owner) "
                    + "as a CSV attachment using the same "
                    + "scope/date semantics and authorization as the JSON endpoints.")
    public ResponseEntity<byte[]> exportGrouped(
            @RequestParam(name = "dataset") String dataset,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/export/grouped - dataset={}, scope={}, tenantId={}, from={}, to={}",
                dataset, scope, tenantId, from, to);

        Resolved r = resolve(scope, tenantId, from, to);
        return csvResponse("grouped-" + dataset, r.context(), r.range(),
                analyticsExportService.groupedCsv(dataset, r.context(), r.range()));
    }

    private Resolved resolve(String scope, UUID tenantId, String from, String to) {
        AnalyticsContext context = scopeResolver.resolve(scope, tenantId);
        AnalyticsDateRange range = AnalyticsDateRange.resolve(from, to);
        return new Resolved(context, range);
    }

    private record Resolved(AnalyticsContext context, AnalyticsDateRange range) {
    }

    private ResponseEntity<byte[]> csvResponse(
            String kind, AnalyticsContext context, AnalyticsDateRange range, String csv) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + analyticsExportService.fileName(kind, context, range) + "\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/trends")
    @PreAuthorize("@rbac.has(authentication, 'report', 'read')")
    @Operation(summary = "Get time-bucketed trend data",
            description = "Returns lead/contact/deal/task counts per time bucket "
                    + "(DAY, WEEK or MONTH auto-selected based on range).")
    public ResponseEntity<ApiResponse<List<AnalyticsTrendResponse>>> getTrends(
            @Parameter(description = "Optional requested scope downgrade")
            @RequestParam(name = "scope", required = false) String scope,
            @Parameter(description = "Range start, ISO-8601 instant (default: to minus 30 days)")
            @RequestParam(name = "from", required = false) String from,
            @Parameter(description = "Range end (exclusive), ISO-8601 instant (default: now)")
            @RequestParam(name = "to", required = false) String to) {

        log.info("GET /api/v1/analytics/trends - scope={}, from={}, to={}", scope, from, to);

        AnalyticsContext context = scopeResolver.resolve(scope);
        AnalyticsDateRange range = AnalyticsDateRange.resolve(from, to);
        List<AnalyticsTrendResponse> response = analyticsService.getTrends(context, range);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
