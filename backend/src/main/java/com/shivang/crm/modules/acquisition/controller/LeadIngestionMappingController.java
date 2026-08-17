package com.shivang.crm.modules.acquisition.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.acquisition.dto.LeadIngestionFieldMappingRequest;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionFieldMappingResponse;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionSourceField;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionTargetField;
import com.shivang.crm.modules.acquisition.dto.MappedLeadData;
import com.shivang.crm.modules.acquisition.dto.ValidatedLeadIngestionData;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEvent;
import com.shivang.crm.modules.acquisition.service.LeadIngestionFieldDiscoveryService;
import com.shivang.crm.modules.acquisition.service.LeadIngestionMappingService;
import com.shivang.crm.modules.acquisition.service.LeadIngestionTargetFieldService;
import com.shivang.crm.modules.acquisition.service.LeadIngestionValidationService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/acquisition/configs/{configId}")
@RequiredArgsConstructor
@Tag(name = "Lead Ingestion Mapping", description = "Discovery, mapping configuration, and preview APIs")
public class LeadIngestionMappingController {

    private final LeadIngestionMappingService leadIngestionMappingService;
    private final LeadIngestionFieldDiscoveryService leadIngestionFieldDiscoveryService;
    private final LeadIngestionTargetFieldService leadIngestionTargetFieldService;
    private final LeadIngestionValidationService leadIngestionValidationService;
    private final TenantContext tenantContext;

    @GetMapping("/events/{eventId}/source-fields")
    @Operation(summary = "Discover source fields from event", description = "Flatten source payload fields from a captured ingestion event")
    public ResponseEntity<ApiResponse<List<LeadIngestionSourceField>>> discoverSourceFieldsFromEvent(
            @Parameter(description = "Ingestion config UUID") @PathVariable UUID configId,
            @Parameter(description = "Ingestion event UUID") @PathVariable UUID eventId) {

        UUID tenantId = requireTenantId();
        LeadIngestionEvent event = leadIngestionMappingService.findEventForDiscovery(tenantId, configId, eventId);
        List<LeadIngestionSourceField> response = leadIngestionFieldDiscoveryService.discoverFields(event.getRawPayload());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/target-fields")
    @Operation(summary = "List mapping target fields", description = "List supported standard/system fields and tenant custom fields")
    public ResponseEntity<ApiResponse<List<LeadIngestionTargetField>>> listTargetFields(
            @Parameter(description = "Ingestion config UUID") @PathVariable UUID configId) {

        UUID tenantId = requireTenantId();
        leadIngestionMappingService.assertConfigOwnership(tenantId, configId);
        List<LeadIngestionTargetField> response = leadIngestionTargetFieldService.listTargetFields(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/mappings")
    @Operation(summary = "List field mappings", description = "List mappings for ingestion config")
    public ResponseEntity<ApiResponse<List<LeadIngestionFieldMappingResponse>>> listMappings(
            @Parameter(description = "Ingestion config UUID") @PathVariable UUID configId) {

        UUID tenantId = requireTenantId();
        List<LeadIngestionFieldMappingResponse> response = leadIngestionMappingService.listMappings(tenantId, configId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/mappings")
    @Operation(summary = "Create field mapping", description = "Create a new field mapping for ingestion config")
    public ResponseEntity<ApiResponse<LeadIngestionFieldMappingResponse>> createMapping(
            @Parameter(description = "Ingestion config UUID") @PathVariable UUID configId,
            @Valid @RequestBody LeadIngestionFieldMappingRequest request) {

        UUID tenantId = requireTenantId();
        LeadIngestionFieldMappingResponse response = leadIngestionMappingService.createMapping(tenantId, configId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/mappings/{mappingId}")
    @Operation(summary = "Update field mapping", description = "Update an existing field mapping")
    public ResponseEntity<ApiResponse<LeadIngestionFieldMappingResponse>> updateMapping(
            @Parameter(description = "Ingestion config UUID") @PathVariable UUID configId,
            @Parameter(description = "Field mapping UUID") @PathVariable UUID mappingId,
            @Valid @RequestBody LeadIngestionFieldMappingRequest request) {

        UUID tenantId = requireTenantId();
        LeadIngestionFieldMappingResponse response = leadIngestionMappingService.updateMapping(tenantId, configId, mappingId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/mappings/{mappingId}")
    @Operation(summary = "Delete field mapping", description = "Soft delete a field mapping")
    public ResponseEntity<ApiResponse<String>> deleteMapping(
            @Parameter(description = "Ingestion config UUID") @PathVariable UUID configId,
            @Parameter(description = "Field mapping UUID") @PathVariable UUID mappingId) {

        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();
        leadIngestionMappingService.deleteMapping(tenantId, userId, configId, mappingId);
        return ResponseEntity.ok(ApiResponse.success("Mapping deleted successfully"));
    }

    @PostMapping("/events/{eventId}/preview")
    @Operation(summary = "Preview mapped lead data", description = "Apply active field mappings to captured event without creating/updating lead")
    public ResponseEntity<ApiResponse<MappedLeadData>> previewMappedData(
            @Parameter(description = "Ingestion config UUID") @PathVariable UUID configId,
            @Parameter(description = "Ingestion event UUID") @PathVariable UUID eventId) {

        UUID tenantId = requireTenantId();
        MappedLeadData response = leadIngestionMappingService.preview(tenantId, configId, eventId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/events/{eventId}/validate-preview")
    @Operation(summary = "Validate and normalize mapped lead data", description = "Run canonical normalization and validation on mapped preview output without creating/updating a lead")
    public ResponseEntity<ApiResponse<ValidatedLeadIngestionData>> validatePreview(
            @Parameter(description = "Ingestion config UUID") @PathVariable UUID configId,
            @Parameter(description = "Ingestion event UUID") @PathVariable UUID eventId) {

        UUID tenantId = requireTenantId();
        MappedLeadData mapped = leadIngestionMappingService.preview(tenantId, configId, eventId);
        ValidatedLeadIngestionData response = leadIngestionValidationService.validateAndNormalize(tenantId, mapped);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private UUID requireTenantId() {
        return tenantContext.requireTenantId();
    }

    private UUID requireUserId() {
        if (!tenantContext.hasUser()) {
            throw new IllegalStateException("User context is not available");
        }
        return tenantContext.getUserId();
    }
}
