package com.shivang.crm.modules.acquisition.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.acquisition.dto.LeadIngestionEventDetailResponse;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionEventSummaryResponse;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEventStatus;
import com.shivang.crm.modules.acquisition.service.LeadIngestionEventQueryService;
import com.shivang.crm.modules.acquisition.service.LeadIngestionProcessingService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/acquisition/configs/{configId}/events")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Lead Ingestion Events", description = "Tenant lead ingestion event history APIs")
public class LeadIngestionEventController {

    private final LeadIngestionEventQueryService leadIngestionEventQueryService;
    private final LeadIngestionProcessingService leadIngestionProcessingService;
    private final TenantContext tenantContext;

    @GetMapping
    @Operation(summary = "List ingestion events", description = "List paginated ingestion events for an ingestion config of the current tenant")
    public ResponseEntity<ApiResponse<List<LeadIngestionEventSummaryResponse>>> listEvents(
            @Parameter(description = "Ingestion config UUID") @PathVariable UUID configId,
            @Parameter(description = "Filter by ingestion event status")
            @RequestParam(required = false) LeadIngestionEventStatus status,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {

        UUID tenantId = requireTenantId();
        Page<LeadIngestionEventSummaryResponse> events = leadIngestionEventQueryService.listEvents(tenantId, configId, status, page, size);

        Map<String, Object> meta = Map.of(
            "page", events.getNumber(),
            "size", events.getSize(),
            "total", events.getTotalElements(),
            "totalPages", events.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(events.getContent(), meta));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Get ingestion event detail", description = "Get full detail of a captured ingestion event including raw payload and sanitized headers")
    public ResponseEntity<ApiResponse<LeadIngestionEventDetailResponse>> getEventDetail(
            @Parameter(description = "Ingestion config UUID") @PathVariable UUID configId,
            @Parameter(description = "Ingestion event UUID") @PathVariable UUID eventId) {

        UUID tenantId = requireTenantId();
        LeadIngestionEventDetailResponse response = leadIngestionEventQueryService.getEventDetail(tenantId, configId, eventId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{eventId}/reprocess")
    @Operation(summary = "Reprocess ingestion event", description = "Reprocess a failed or rejected ingestion event using current mapping/configuration. Only FAILED/REJECTED events can be reprocessed.")
    public ResponseEntity<ApiResponse<LeadIngestionEventDetailResponse>> reprocessEvent(
            @Parameter(description = "Ingestion config UUID") @PathVariable UUID configId,
            @Parameter(description = "Ingestion event UUID") @PathVariable UUID eventId) {

        UUID tenantId = requireTenantId();
        // Reprocess is tenant-scoped, uses current mapping, preserves raw payload, increments attempt count.
        // Concurrency is handled via pessimistic lock + terminal checks inside service.
        var reprocessed = leadIngestionProcessingService.reprocessEvent(tenantId, configId, eventId);
        // Return detail view of the updated event
        LeadIngestionEventDetailResponse response = leadIngestionEventQueryService.getEventDetail(tenantId, configId, eventId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private UUID requireTenantId() {
        return tenantContext.requireTenantId();
    }
}
