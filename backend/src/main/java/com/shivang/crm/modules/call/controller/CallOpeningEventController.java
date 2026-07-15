package com.shivang.crm.modules.call.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.dialer.entity.CallOpeningEvent;
import com.shivang.crm.modules.dialer.service.CallOpeningEventService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/calls/opening-events")
@RequiredArgsConstructor
public class CallOpeningEventController {

    private final CallOpeningEventService eventService;
    private final TenantContext tenantContext;

    record CallOpeningEventResponse(UUID id, UUID tenantId, UUID userId, String agentId, UUID callId, String externalCallId, String providerKey, String triggerKey, java.util.Map<String, Object> instruction, String deliveryStatus, java.time.Instant createdAt, java.time.Instant deliveredAt) {}

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<CallOpeningEventResponse>>> pending(@RequestParam(required = false) String agentId) {
        UUID tenantId = tenantContext.getTenantId();
        List<CallOpeningEvent> events;
        if (agentId != null && !agentId.isBlank()) {
            events = eventService.findPendingForAgent(tenantId, agentId);
        } else {
            events = eventService.findPendingForTenant(tenantId);
        }
        var resp = events.stream().map(e -> new CallOpeningEventResponse(e.getId(), e.getTenantId(), e.getUserId(), e.getAgentId(), e.getCallId(), e.getExternalCallId(), e.getProviderKey(), e.getTriggerKey(), e.getInstruction(), e.getDeliveryStatus(), e.getCreatedAt(), e.getDeliveredAt())).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @PostMapping("/{eventId}/delivered")
    public ResponseEntity<ApiResponse<Void>> markDelivered(@PathVariable UUID eventId) {
        eventService.markDelivered(eventId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
