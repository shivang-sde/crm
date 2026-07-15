package com.shivang.crm.modules.deal.controller;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
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
import com.shivang.crm.modules.deal.dto.DealCreateRequest;
import com.shivang.crm.modules.deal.dto.DealResponse;
import com.shivang.crm.modules.deal.dto.DealUpdateRequest;
import com.shivang.crm.modules.deal.service.DealService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Deals", description = "Deal/Opportunity Management APIs")
public class DealController {

    private final DealService dealService;
    private final TenantContext tenantContext;

    /**
     * Create a new deal
     */
    @PostMapping
    @Operation(summary = "Create a new deal", description = "Create a deal/opportunity")
    public ResponseEntity<ApiResponse<DealResponse>> createDeal(
            @Valid @RequestBody DealCreateRequest request) {

        log.info("POST /api/v1/deals - Creating deal");

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        DealResponse dealResponse = dealService.createDeal(tenantId, userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(dealResponse));
    }

    /**
     * Get all deals with filtering and pagination
     */
    @GetMapping
    @Operation(summary = "List deals", description = "Get all deals with filtering, searching, and pagination")
    public ResponseEntity<ApiResponse<java.util.List<DealResponse>>> listDeals(
            @Parameter(description = "Search term") @RequestParam(required = false) String search,

            @Parameter(description = "Stage UUID") @RequestParam(required = false) UUID stage,

            @Parameter(description = "Account UUID") @RequestParam(required = false) UUID accountId,

            @Parameter(description = "Contact UUID") @RequestParam(required = false) UUID contactId,

            @Parameter(description = "Owner user UUID") @RequestParam(required = false) UUID owner,

            @Parameter(description = "Filter by won deals") @RequestParam(required = false) Boolean won,

            @Parameter(description = "Filter by lost deals") @RequestParam(required = false) Boolean lost,

            @Parameter(description = "Expected close date from") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closeDateFrom,

            @Parameter(description = "Expected close date to") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closeDateTo,

            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/deals - Listing deals with filters");

        UUID tenantId = currentTenantId();

        Page<DealResponse> deals = dealService.listDeals(
                tenantId, stage, accountId, contactId, owner, search, won, lost, closeDateFrom, closeDateTo, page,
                size);

        Map<String, Object> meta = Map.of(
                "page", deals.getNumber(),
                "size", deals.getSize(),
                "total", deals.getTotalElements(),
                "totalPages", deals.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(deals.getContent(), meta));
    }

    /**
     * Get deal by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get deal details", description = "Get complete details of a specific deal")
    public ResponseEntity<ApiResponse<DealResponse>> getDeal(
            @Parameter(description = "Deal UUID") @PathVariable UUID id) {

        log.info("GET /api/v1/deals/{} - Getting deal details", id);

        UUID tenantId = currentTenantId();

        DealResponse dealResponse = dealService.getDealById(id, tenantId);

        return ResponseEntity.ok(ApiResponse.success(dealResponse));
    }

    /**
     * Update a deal
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update deal", description = "Update deal information")
    public ResponseEntity<ApiResponse<DealResponse>> updateDeal(
            @Parameter(description = "Deal UUID") @PathVariable UUID id,

            @Valid @RequestBody DealUpdateRequest request) {

        log.info("PUT /api/v1/deals/{} - Updating deal", id);

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        DealResponse dealResponse = dealService.updateDeal(id, tenantId, userId, request);

        return ResponseEntity.ok(ApiResponse.success(dealResponse));
    }

    /**
     * Delete a deal
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete deal", description = "Delete a deal")
    public ResponseEntity<ApiResponse<String>> deleteDeal(
            @Parameter(description = "Deal UUID") @PathVariable UUID id) {

        log.info("DELETE /api/v1/deals/{} - Deleting deal", id);

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        dealService.deleteDeal(id, tenantId, userId);

        return ResponseEntity.ok(ApiResponse.success("Deal deleted successfully"));
    }

    /**
     * Change deal stage
     */
    @PatchMapping("/{id}/stage")
    @Operation(summary = "Change deal stage", description = "Move deal to a different stage")
    public ResponseEntity<ApiResponse<DealResponse>> changeStage(
            @Parameter(description = "Deal UUID") @PathVariable UUID id,

            @RequestBody Map<String, UUID> request) {

        log.info("PATCH /api/v1/deals/{}/stage - Changing stage", id);

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        UUID stageId = request.get("stageId");

        DealResponse dealResponse = dealService.changeStage(id, tenantId, stageId, userId);

        return ResponseEntity.ok(ApiResponse.success(dealResponse));
    }

    /**
     * Mark deal as won
     */
    // @PatchMapping("/{id}/won")
    // @Operation(summary = "Mark deal as won", description = "Mark a deal as won")
    // public ResponseEntity<ApiResponse<DealResponse>> markDealWon(
    // @Parameter(description = "Deal UUID")
    // @PathVariable UUID id) {

    // log.info("PATCH /api/v1/deals/{}/won - Marking deal as won", id);

    // UUID tenantId = currentTenantId();
    // UUID userId = currentUserId();

    // DealResponse dealResponse = dealService.markDealWon(id, tenantId, userId);

    // return ResponseEntity.ok(ApiResponse.success(dealResponse));
    // }

    /**
     * Mark deal as lost
     */
    // @PatchMapping("/{id}/lost")
    // @Operation(summary = "Mark deal as lost", description = "Mark a deal as
    // lost")
    // public ResponseEntity<ApiResponse<DealResponse>> markDealLost(
    // @Parameter(description = "Deal UUID")
    // @PathVariable UUID id) {

    // log.info("PATCH /api/v1/deals/{}/lost - Marking deal as lost", id);

    // UUID tenantId = currentTenantId();
    // UUID userId = currentUserId();

    // DealResponse dealResponse = dealService.markDealLost(id, tenantId, userId);

    // return ResponseEntity.ok(ApiResponse.success(dealResponse));
    // }

    /**
     * Assign deal to a user
     */
    @PutMapping("/{id}/assign")
    @Operation(summary = "Assign deal", description = "Assign a deal to another user")
    public ResponseEntity<ApiResponse<DealResponse>> assignDeal(
            @Parameter(description = "Deal UUID") @PathVariable UUID id,

            @RequestBody Map<String, UUID> request) {

        log.info("PUT /api/v1/deals/{}/assign - Assigning deal", id);

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        UUID ownerUserId = request.get("ownerUserId");

        DealResponse dealResponse = dealService.assignDeal(id, tenantId, ownerUserId, userId);

        return ResponseEntity.ok(ApiResponse.success(dealResponse));
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
