package com.shivang.crm.modules.lead.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.lead.dto.LeadConvertRequest;
import com.shivang.crm.modules.lead.dto.LeadConvertResponse;
import com.shivang.crm.modules.lead.dto.LeadCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadResponse;
import com.shivang.crm.modules.lead.dto.LeadUpdateRequest;
import com.shivang.crm.modules.lead.service.LeadService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Leads", description = "Lead Management APIs")
public class LeadController {

    private final LeadService leadService;
    private final TenantContext tenantContext;

    /**
     * Create a new lead
     */
    @PostMapping
    @Operation(summary = "Create a new lead", description = "Create a lead with custom fields")
    public ResponseEntity<ApiResponse<LeadResponse>> createLead(
            @Valid @RequestBody LeadCreateRequest request) {

        log.info("POST /api/v1/leads - Creating lead");

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        LeadResponse leadResponse = leadService.createLead(tenantId, userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(leadResponse));
    }

    /**
     * Get all leads with filtering and pagination
     */
    @GetMapping
    @Operation(summary = "List leads", description = "Get all leads with filtering, searching, and pagination")
    public ResponseEntity<ApiResponse<java.util.List<LeadResponse>>> listLeads(
            @Parameter(description = "Search term")
            @RequestParam(required = false) String search,

            @Parameter(description = "Status UUID")
            @RequestParam(required = false) UUID status,

            @Parameter(description = "Source UUID")
            @RequestParam(required = false) UUID source,

            @Parameter(description = "Owner user UUID")
            @RequestParam(required = false) UUID owner,

            @Parameter(description = "Show converted leads")
            @RequestParam(required = false) Boolean converted,

            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/leads - Listing leads with filters");

        UUID tenantId = currentTenantId();

        Page<LeadResponse> leads = leadService.listLeads(
            tenantId, status, source, owner, search, converted, page, size
        );

        Map<String, Object> meta = Map.of(
            "page", leads.getNumber(),
            "size", leads.getSize(),
            "total", leads.getTotalElements(),
            "totalPages", leads.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(leads.getContent(), meta));
    }

    /**
     * Get lead by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get lead details", description = "Get complete details of a specific lead")
    public ResponseEntity<ApiResponse<LeadResponse>> getLead(
            @Parameter(description = "Lead UUID")
            @PathVariable UUID id) {

        log.info("GET /api/v1/leads/{} - Getting lead details", id);

        UUID tenantId = currentTenantId();

        LeadResponse leadResponse = leadService.getLeadById(id, tenantId);

        return ResponseEntity.ok(ApiResponse.success(leadResponse));
    }

    /**
     * Update a lead
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update lead", description = "Update lead information")
    public ResponseEntity<ApiResponse<LeadResponse>> updateLead(
            @Parameter(description = "Lead UUID")
            @PathVariable UUID id,

            @Valid @RequestBody LeadUpdateRequest request) {

        log.info("PUT /api/v1/leads/{} - Updating lead", id);

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        LeadResponse leadResponse = leadService.updateLead(id, tenantId, userId, request);

        return ResponseEntity.ok(ApiResponse.success(leadResponse));
    }

    /**
     * Delete a lead
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete lead", description = "Delete a lead")
    public ResponseEntity<ApiResponse<String>> deleteLead(
            @Parameter(description = "Lead UUID")
            @PathVariable UUID id) {

        log.info("DELETE /api/v1/leads/{} - Deleting lead", id);

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        leadService.deleteLead(id, tenantId, userId);

        return ResponseEntity.ok(ApiResponse.success("Lead deleted successfully"));
    }

    /**
     * Assign lead to a user
     */
    @PutMapping("/{id}/assign")
    @Operation(summary = "Assign lead", description = "Assign a lead to another user")
    public ResponseEntity<ApiResponse<LeadResponse>> assignLead(
            @Parameter(description = "Lead UUID")
            @PathVariable UUID id,

            @RequestBody Map<String, UUID> request) {

        log.info("PUT /api/v1/leads/{}/assign - Assigning lead", id);

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        UUID ownerUserId = request.get("ownerUserId");

        LeadResponse leadResponse = leadService.assignLead(id, tenantId, ownerUserId, userId);

        return ResponseEntity.ok(ApiResponse.success(leadResponse));
    }

    /**
     * Change lead status
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "Change lead status", description = "Change the status of a lead")
    public ResponseEntity<ApiResponse<LeadResponse>> changeStatus(
            @Parameter(description = "Lead UUID")
            @PathVariable UUID id,

            @RequestBody Map<String, UUID> request) {

        log.info("PUT /api/v1/leads/{}/status - Changing status", id);

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        UUID statusId = request.get("statusId");

        LeadResponse leadResponse = leadService.changeStatus(id, tenantId, statusId, userId);

        return ResponseEntity.ok(ApiResponse.success(leadResponse));
    }

    @PostMapping("/{id}/convert")
    @Operation(summary = "Convert lead", description = "Convert a lead into an account and a primary contact")
    public ResponseEntity<ApiResponse<LeadConvertResponse>> convertLead(
            @Parameter(description = "Lead UUID")
            @PathVariable UUID id,
            @RequestBody(required = false) LeadConvertRequest request) {

        log.info("POST /api/v1/leads/{}/convert - Converting lead", id);

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        LeadConvertResponse response = leadService.convertLead(id, tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
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
