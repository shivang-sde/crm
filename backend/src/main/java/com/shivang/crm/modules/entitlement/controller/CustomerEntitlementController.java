package com.shivang.crm.modules.entitlement.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.entitlement.dto.CustomerEntitlementResponse;
import com.shivang.crm.modules.entitlement.dto.CustomerEntitlementUpdateRequest;
import com.shivang.crm.modules.entitlement.dto.UpcomingRenewalResponse;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.service.CustomerEntitlementService;
import com.shivang.crm.shared.dto.ApiResponse;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/entitlements")
@RequiredArgsConstructor
public class CustomerEntitlementController {

    private final CustomerEntitlementService entitlementService;
    private final TenantContext tenantContext;

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<CustomerEntitlementResponse>>> list(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) UUID offeringId,
            @RequestParam(required = false) EntitlementStatus status,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) Boolean renewable,
            @RequestParam(required = false) String endDateFrom,
            @RequestParam(required = false) String endDateTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = currentTenantId();
        Page<CustomerEntitlementResponse> result = entitlementService.list(
                tenantId,
                accountId,
                contactId,
                offeringId,
                status,
                ownerUserId,
                renewable,
                endDateFrom == null ? null : java.time.LocalDate.parse(endDateFrom),
                endDateTo == null ? null : java.time.LocalDate.parse(endDateTo),
                search,
                page,
                size);
        Map<String, Object> meta = Map.of(
                "page", result.getNumber(),
                "size", result.getSize(),
                "total", result.getTotalElements(),
                "totalPages", result.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), meta));
    }

    @GetMapping("/upcoming-renewals")
    public ResponseEntity<ApiResponse<java.util.List<UpcomingRenewalResponse>>> upcomingRenewals(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) EntitlementStatus status,
            @RequestParam(defaultValue = "30") int daysAhead,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (daysAhead < 1 || daysAhead > 365) {
            throw new BusinessException("INVALID_DAYS_AHEAD", "daysAhead must be between 1 and 365");
        }
        UUID tenantId = currentTenantId();
        Page<UpcomingRenewalResponse> result = entitlementService.findUpcomingRenewals(
                tenantId, accountId, ownerUserId, status, daysAhead, page, size);
        Map<String, Object> meta = Map.of(
                "page", result.getNumber(),
                "size", result.getSize(),
                "total", result.getTotalElements(),
                "totalPages", result.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), meta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerEntitlementResponse>> getById(@PathVariable UUID id) {
        UUID tenantId = currentTenantId();
        return ResponseEntity.ok(ApiResponse.success(entitlementService.getById(id, tenantId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerEntitlementResponse>> update(
            @PathVariable UUID id,
            @RequestBody CustomerEntitlementUpdateRequest request) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        return ResponseEntity.ok(ApiResponse.success(entitlementService.update(id, tenantId, userId, request)));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<String>> activate(@PathVariable UUID id) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        entitlementService.activate(id, tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success("Entitlement activated successfully"));
    }

    @PatchMapping("/{id}/suspend")
    public ResponseEntity<ApiResponse<String>> suspend(@PathVariable UUID id) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        entitlementService.suspend(id, tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success("Entitlement suspended successfully"));
    }

    @PatchMapping("/{id}/terminate")
    public ResponseEntity<ApiResponse<String>> terminate(@PathVariable UUID id) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        entitlementService.terminate(id, tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success("Entitlement terminated successfully"));
    }

    private UUID currentTenantId() {
        return tenantContext.getTenantId();
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("User authentication is not available");
        }
        return UUID.fromString(authentication.getPrincipal().toString());
    }
}
