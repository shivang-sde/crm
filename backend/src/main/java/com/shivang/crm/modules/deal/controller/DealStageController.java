package com.shivang.crm.modules.deal.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.deal.dto.DealStageCreateRequest;
import com.shivang.crm.modules.deal.dto.DealStageResponse;
import com.shivang.crm.modules.deal.dto.DealStageUpdateRequest;
import com.shivang.crm.modules.deal.service.DealStageService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/deal-stages")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Deal Stages", description = "Deal Stage Management APIs")
public class DealStageController {

    private final DealStageService dealStageService;
    private final TenantContext tenantContext;

    /**
     * Create a new deal stage
     */
    @PostMapping
    @Operation(summary = "Create a new deal stage", description = "Create a new deal stage for the pipeline")
    public ResponseEntity<ApiResponse<DealStageResponse>> createDealStage(
            @Valid @RequestBody DealStageCreateRequest request) {

        log.info("POST /api/v1/deal-stages - Creating deal stage");

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        DealStageResponse stageResponse = dealStageService.createDealStage(tenantId, userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(stageResponse));
    }

    /**
     * Get all deal stages for a tenant
     */
    @GetMapping
    @Operation(summary = "List deal stages", description = "Get all deal stages for this tenant")
    public ResponseEntity<ApiResponse<List<DealStageResponse>>> listDealStages() {

        log.info("GET /api/v1/deal-stages - Listing deal stages");

        UUID tenantId = currentTenantId();

        List<DealStageResponse> stages = dealStageService.listDealStages(tenantId);

        return ResponseEntity.ok(ApiResponse.success(stages));
    }

    /**
     * Get deal stage by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get deal stage details", description = "Get complete details of a specific deal stage")
    public ResponseEntity<ApiResponse<DealStageResponse>> getDealStage(
            @Parameter(description = "Deal Stage UUID")
            @PathVariable UUID id) {

        log.info("GET /api/v1/deal-stages/{} - Getting deal stage details", id);

        UUID tenantId = currentTenantId();

        DealStageResponse stageResponse = dealStageService.getDealStageById(id, tenantId);

        return ResponseEntity.ok(ApiResponse.success(stageResponse));
    }

    /**
     * Update a deal stage
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update deal stage", description = "Update deal stage information")
    public ResponseEntity<ApiResponse<DealStageResponse>> updateDealStage(
            @Parameter(description = "Deal Stage UUID")
            @PathVariable UUID id,

            @Valid @RequestBody DealStageUpdateRequest request) {

        log.info("PUT /api/v1/deal-stages/{} - Updating deal stage", id);

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        DealStageResponse stageResponse = dealStageService.updateDealStage(id, tenantId, userId, request);

        return ResponseEntity.ok(ApiResponse.success(stageResponse));
    }

    /**
     * Delete a deal stage
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete deal stage", description = "Delete a deal stage")
    public ResponseEntity<ApiResponse<String>> deleteDealStage(
            @Parameter(description = "Deal Stage UUID")
            @PathVariable UUID id) {

        log.info("DELETE /api/v1/deal-stages/{} - Deleting deal stage", id);

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        dealStageService.deleteDealStage(id, tenantId, userId);

        return ResponseEntity.ok(ApiResponse.success("Deal stage deleted successfully"));
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
