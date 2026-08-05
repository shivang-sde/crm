package com.shivang.crm.modules.catalog.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.catalog.dto.OfferingCreateRequest;
import com.shivang.crm.modules.catalog.dto.OfferingResponse;
import com.shivang.crm.modules.catalog.dto.OfferingUpdateRequest;
import com.shivang.crm.modules.catalog.enums.BillingType;
import com.shivang.crm.modules.catalog.enums.OfferingType;
import com.shivang.crm.modules.catalog.service.OfferingService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/offerings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Offerings", description = "Offering Catalog APIs")
public class OfferingController {

    private final OfferingService offeringService;
    private final TenantContext tenantContext;

    @PostMapping
    @Operation(summary = "Create offering", description = "Create a new catalog offering")
    public ResponseEntity<ApiResponse<OfferingResponse>> createOffering(@RequestBody OfferingCreateRequest request) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        OfferingResponse response = offeringService.createOffering(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get offering", description = "Get an offering by id")
    public ResponseEntity<ApiResponse<OfferingResponse>> getOffering(@PathVariable UUID id) {
        UUID tenantId = currentTenantId();
        return ResponseEntity.ok(ApiResponse.success(offeringService.getOfferingById(id, tenantId)));
    }

    @GetMapping
    @Operation(summary = "List offerings", description = "List offerings with filters and pagination")
    public ResponseEntity<ApiResponse<java.util.List<OfferingResponse>>> listOfferings(
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            @Parameter(description = "Offering type filter") @RequestParam(required = false) OfferingType offeringType,
            @Parameter(description = "Billing type filter") @RequestParam(required = false) BillingType billingType,
            @Parameter(description = "Active filter") @RequestParam(required = false) Boolean active,
            @Parameter(description = "Owner user UUID") @RequestParam(required = false) UUID ownerUserId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = currentTenantId();
        Page<OfferingResponse> offerings = offeringService.listOfferings(tenantId, search, offeringType, billingType,
                active, ownerUserId, page, size);
        Map<String, Object> meta = Map.of(
                "page", offerings.getNumber(),
                "size", offerings.getSize(),
                "total", offerings.getTotalElements(),
                "totalPages", offerings.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(offerings.getContent(), meta));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update offering", description = "Update an existing offering")
    public ResponseEntity<ApiResponse<OfferingResponse>> updateOffering(@PathVariable UUID id,
            @RequestBody OfferingUpdateRequest request) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        return ResponseEntity.ok(ApiResponse.success(offeringService.updateOffering(id, tenantId, userId, request)));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate offering", description = "Activate an offering")
    public ResponseEntity<ApiResponse<String>> activateOffering(@PathVariable UUID id) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        offeringService.activateOffering(id, tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success("Offering activated successfully"));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate offering", description = "Deactivate an offering")
    public ResponseEntity<ApiResponse<String>> deactivateOffering(@PathVariable UUID id) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        offeringService.deactivateOffering(id, tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success("Offering deactivated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete offering", description = "Soft delete an offering")
    public ResponseEntity<ApiResponse<String>> deleteOffering(@PathVariable UUID id) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        offeringService.deleteOffering(id, tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success("Offering deleted successfully"));
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
