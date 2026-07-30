package com.shivang.crm.modules.call.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.dialer.entity.CallOpeningEvent;
import com.shivang.crm.modules.dialer.service.CallOpeningEventService;
import com.shivang.crm.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/calls/opening-events")
@RequiredArgsConstructor
public class CallOpeningEventController {

    private final CallOpeningEventService eventService;
    private final TenantContext tenantContext;

    public record CallOpeningEventResponse(
            UUID id,
            UUID tenantId,
            UUID userId,
            String agentId,
            UUID callId,
            String externalCallId,
            String providerKey,
            String triggerKey,
            Map<String, Object> instruction,
            String deliveryStatus,
            Instant createdAt,
            Instant deliveredAt) {
    }

    public record DeliveryResponse(
            UUID eventId,
            String status,
            Instant deliveredAt) {
    }

   @GetMapping("/pending")
        public ResponseEntity<ApiResponse<List<CallOpeningEventResponse>>> pending() {

    UUID tenantId = requireTenantId();
    UUID userId = requireUserId();

    List<CallOpeningEvent> events =
            eventService.findPendingForTenantAndUser(
                    tenantId,
                    userId
            );

        log.info(
            "Pending call-opening events tenant={} user={} count={} eventIds={}",
            tenantId,
            userId,
            events.size(),
            events.stream()
                    .map(CallOpeningEvent::getId)
                    .toList()
    );

    List<CallOpeningEventResponse> response =
            events.stream()
                    .map(this::toResponse)
                    .toList();

    return ResponseEntity.ok(
            ApiResponse.success(response)
    );
}

    @PostMapping("/{eventId}/delivered")
    public ResponseEntity<ApiResponse<DeliveryResponse>> markDelivered(
            @PathVariable UUID eventId) {

        UUID tenantId = requireTenantId();
        UUID userId = tenantContext.getUserId();

        CallOpeningEvent event = eventService.markDelivered(
                tenantId,
                userId,
                eventId
        );

        DeliveryResponse response = new DeliveryResponse(
                event.getId(),
                event.getDeliveryStatus(),
                event.getDeliveredAt()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private CallOpeningEventResponse toResponse(CallOpeningEvent event) {
        return new CallOpeningEventResponse(
                event.getId(),
                event.getTenantId(),
                event.getUserId(),
                event.getAgentId(),
                event.getCallId(),
                event.getExternalCallId(),
                event.getProviderKey(),
                event.getTriggerKey(),
                event.getInstruction(),
                event.getDeliveryStatus(),
                event.getCreatedAt(),
                event.getDeliveredAt()
        );
    }

    private UUID requireTenantId() {
        if (!tenantContext.hasTenant()) {
            throw new IllegalStateException(
                    "Tenant context is not available"
            );
        }

        return tenantContext.getTenantId();
    }

    private UUID requireUserId() {
        if (!tenantContext.hasUser()) {
            throw new IllegalStateException(
                    "User context is not available"
            );
        }

        return tenantContext.getUserId();
    }
}