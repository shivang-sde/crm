package com.shivang.crm.modules.feedback.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.account.service.AccountService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.feedback.dto.RiskSignalResponse;
import com.shivang.crm.modules.feedback.dto.RiskSignalResponse.RiskSignalSeverity;
import com.shivang.crm.modules.feedback.dto.RiskSignalResponse.RiskSignalType;
import com.shivang.crm.modules.feedback.service.RiskSignalPage;
import com.shivang.crm.modules.feedback.service.RiskSignalService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * L3.4-P1: deterministic customer risk signals.
 *
 * <p>Two reads, deliberately at different levels:
 * <ul>
 *   <li>{@code /api/v1/accounts/{accountId}/health/risk} — one customer.</li>
 *   <li>{@code /api/v1/customer-health/risk-signals} — every customer the caller
 *       can see, for triage. It is a separate path rather than a nested account
 *       route because it is not scoped to one account, and it takes no
 *       {@code accountId} path variable.</li>
 * </ul>
 *
 * <p>Both routes require {@code account:read} and add no new permission.
 * The account route also enforces the parent-account record scope with
 * {@link AccountService#assertAccountAccessible}, the same nested-resource rule
 * used by account activities and notes. The cross-account route resolves
 * {@code account:read} in SQL and returns signals attached to accounts in that
 * scope. A deal or entitlement with no linked account remains governed by its
 * source-domain scope because it has no account to authorize.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Customer Health Risk Signals",
        description = "Deterministic customer risk signals derived from existing CRM records")
public class RiskSignalController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RiskSignalService riskSignalService;
    private final TenantContext tenantContext;
    private final AccountService accountService;

    @GetMapping("/api/v1/accounts/{accountId}/health/risk")
    @PreAuthorize("@rbac.has(authentication, 'account', 'read')")
    @Operation(summary = "Risk signals for one account",
            description = "Returns deterministic customer risk signals for one account, derived "
                    + "from existing CRM records: negative NPS, low CSAT, high CES, recent "
                    + "complaint, SLA-breached ticket, entitlement approaching expiry or its "
                    + "renewal window, expired entitlement with no later coverage recorded, and "
                    + "recent lost deal. The account must be visible within the caller's "
                    + "account:read scope. Each signal names the source record it came from. "
                    + "This is not a health score and contains no predictive or payment "
                    + "information.")
    public ResponseEntity<ApiResponse<List<RiskSignalResponse>>> riskForAccount(
            @PathVariable UUID accountId,
            @Parameter(description = "Inclusive lower bound, ISO-8601 instant")
            @RequestParam(required = false) String from,
            @Parameter(description = "Exclusive upper bound, ISO-8601 instant")
            @RequestParam(required = false) String to) {

        UUID tenantId = currentTenantId();
        accountService.assertAccountAccessible(tenantId, accountId, "read");

        List<RiskSignalResponse> signals = riskSignalService.riskSignals(
                tenantId, accountId, from, to);

        return ResponseEntity.ok(ApiResponse.success(
                signals, java.util.Map.of("total", signals.size())));
    }

    @GetMapping("/api/v1/customer-health/risk-signals")
    @PreAuthorize("@rbac.has(authentication, 'account', 'read')")
    @Operation(summary = "Risk signals across accounts",
            description = "Returns deterministic customer risk signals attached to accounts within "
                    + "the caller's account:read scope, for triage. Deal and entitlement signals "
                    + "with no linked account are governed by their source-domain read scope. "
                    + "Paginated and filtered by signal type and severity. Deterministic rules "
                    + "only: no composite health score, no churn prediction, and no payment "
                    + "information.")
    public ResponseEntity<ApiResponse<List<RiskSignalResponse>>> riskSignalsAcrossAccounts(
            @Parameter(description = "Inclusive lower bound, ISO-8601 instant")
            @RequestParam(required = false) String from,
            @Parameter(description = "Exclusive upper bound, ISO-8601 instant")
            @RequestParam(required = false) String to,
            @Parameter(description = "Restrict to one signal type")
            @RequestParam(required = false) RiskSignalType type,
            @Parameter(description = "Restrict to one severity")
            @RequestParam(required = false) RiskSignalSeverity severity,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size) {

        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, size <= 0 ? DEFAULT_PAGE_SIZE : size);

        RiskSignalPage result = riskSignalService.riskSignalsPage(
                currentTenantId(), null, from, to, type, severity, safePage, safeSize);

        return ResponseEntity.ok(ApiResponse.success(
                result.signals(),
                java.util.Map.of(
                        "page", result.page(),
                        "size", result.size(),
                        "total", result.totalElements(),
                        "totalPages", result.totalPages())));
    }

    private UUID currentTenantId() {
        return tenantContext.requireTenantId();
    }
}