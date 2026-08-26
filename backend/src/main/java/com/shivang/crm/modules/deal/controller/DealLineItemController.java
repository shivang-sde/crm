package com.shivang.crm.modules.deal.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.deal.dto.DealLineItemCreateRequest;
import com.shivang.crm.modules.deal.dto.DealLineItemResponse;
import com.shivang.crm.modules.deal.dto.DealLineItemUpdateRequest;
import com.shivang.crm.modules.deal.service.DealLineItemService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/deals/{dealId}/line-items")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Deal Line Items", description = "Deal line item management APIs")
public class DealLineItemController {

    private final DealLineItemService dealLineItemService;
    private final TenantContext tenantContext;

    @GetMapping
    @PreAuthorize("@rbac.has(authentication, 'deal', 'read')")
    @Operation(summary = "List deal line items", description = "List line items for a deal")
    public ResponseEntity<ApiResponse<java.util.List<DealLineItemResponse>>> listLineItems(
            @Parameter(description = "Deal UUID") @PathVariable UUID dealId) {
        UUID tenantId = currentTenantId();
        return ResponseEntity.ok(ApiResponse.success(dealLineItemService.listLineItems(tenantId, dealId)));
    }

    @GetMapping("/{lineItemId}")
    @PreAuthorize("@rbac.has(authentication, 'deal', 'read')")
    @Operation(summary = "Get deal line item", description = "Get a line item for a deal")
    public ResponseEntity<ApiResponse<DealLineItemResponse>> getLineItem(
            @Parameter(description = "Deal UUID") @PathVariable UUID dealId,
            @Parameter(description = "Line item UUID") @PathVariable UUID lineItemId) {
        UUID tenantId = currentTenantId();
        return ResponseEntity.ok(ApiResponse.success(dealLineItemService.getLineItem(tenantId, dealId, lineItemId)));
    }

    @PostMapping
    @PreAuthorize("@rbac.has(authentication, 'deal', 'write')")
    @Operation(summary = "Create deal line item", description = "Create a line item for a deal")
    public ResponseEntity<ApiResponse<DealLineItemResponse>> createLineItem(
            @Parameter(description = "Deal UUID") @PathVariable UUID dealId,
            @Valid @RequestBody DealLineItemCreateRequest request) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        DealLineItemResponse response = dealLineItemService.createLineItem(tenantId, dealId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{lineItemId}")
    @PreAuthorize("@rbac.has(authentication, 'deal', 'write')")
    @Operation(summary = "Update deal line item", description = "Update a line item for a deal")
    public ResponseEntity<ApiResponse<DealLineItemResponse>> updateLineItem(
            @Parameter(description = "Deal UUID") @PathVariable UUID dealId,
            @Parameter(description = "Line item UUID") @PathVariable UUID lineItemId,
            @Valid @RequestBody DealLineItemUpdateRequest request) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        return ResponseEntity.ok(ApiResponse.success(dealLineItemService.updateLineItem(tenantId, dealId, lineItemId, userId, request)));
    }

    @DeleteMapping("/{lineItemId}")
    @PreAuthorize("@rbac.has(authentication, 'deal', 'delete')")
    @Operation(summary = "Delete deal line item", description = "Soft delete a line item from a deal")
    public ResponseEntity<ApiResponse<String>> deleteLineItem(
            @Parameter(description = "Deal UUID") @PathVariable UUID dealId,
            @Parameter(description = "Line item UUID") @PathVariable UUID lineItemId) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        dealLineItemService.deleteLineItem(tenantId, dealId, lineItemId, userId);
        return ResponseEntity.ok(ApiResponse.success("Deal line item deleted successfully"));
    }

    private UUID currentTenantId() {
        return tenantContext.getTenantId();
    }

    private UUID currentUserId() {
        return tenantContext.getUserId();
    }
}
