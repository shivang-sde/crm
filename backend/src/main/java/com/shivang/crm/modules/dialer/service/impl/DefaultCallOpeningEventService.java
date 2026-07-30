package com.shivang.crm.modules.dialer.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.dialer.dto.CallOpeningInstruction;
import com.shivang.crm.modules.dialer.entity.CallOpeningEvent;
import com.shivang.crm.modules.dialer.repository.CallOpeningEventRepository;
import com.shivang.crm.modules.dialer.service.CallOpeningEventService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultCallOpeningEventService implements CallOpeningEventService {

    private final CallOpeningEventRepository repo;
    ObjectMapper objectMapper = new ObjectMapper();

    @Override
public CallOpeningEvent createEvent(
        UUID tenantId,
        UUID userId,
        String agentId,
        UUID callId,
        String externalCallId,
        String providerKey,
        String triggerKey,
        CallOpeningInstruction instruction) {

    Map<String, Object> instructionMap =
            instruction == null
                    ? null
                    : objectMapper.convertValue(
                            instruction,
                            new com.fasterxml.jackson.core.type.TypeReference<
                                    Map<String, Object>>() {
                            }
                    );

    CallOpeningEvent event = CallOpeningEvent.builder()
            .tenantId(tenantId)
            .userId(userId)
            .agentId(agentId)
            .callId(callId)
            .externalCallId(externalCallId)
            .providerKey(providerKey)
            .triggerKey(triggerKey)
            .instruction(instructionMap)
            .deliveryStatus("PENDING")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    return repo.save(event);
}

    @Override
    @Transactional(readOnly = true)
    public List<CallOpeningEvent> findPendingForTenantAndUser(
            UUID tenantId,
            UUID userId) {

        return repo.findPendingForTenantAndUser(
                tenantId,
                userId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<CallOpeningEvent> findPendingForAgent(
            UUID tenantId,
            String agentId) {

        return repo.findByTenantIdAndAgentIdAndDeliveryStatus(
                tenantId,
                agentId,
                "PENDING"
        );
    }

    @Override
    public CallOpeningEvent markDelivered(
            UUID tenantId,
            UUID userId,
            UUID eventId) {

        var event = repo
                .findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Call opening event not found"
                ));

        /*
         * A targeted event may only be delivered by its intended user.
         * A null userId means tenant-wide/broadcast event.
         */
        if (event.getUserId() != null
                && !event.getUserId().equals(userId)) {

            throw new SecurityException(
                    "Call opening event belongs to another user"
            );
        }

        if (!"DELIVERED".equals(event.getDeliveryStatus())) {
            event.setDeliveryStatus("DELIVERED");
            event.setDeliveredAt(Instant.now());
            event = repo.save(event);
        }

        return event;
    }
}