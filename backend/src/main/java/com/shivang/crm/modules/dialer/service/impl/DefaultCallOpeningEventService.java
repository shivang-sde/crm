package com.shivang.crm.modules.dialer.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.dialer.dto.CallOpeningInstruction;
import com.shivang.crm.modules.dialer.entity.CallOpeningEvent;
import com.shivang.crm.modules.dialer.repository.CallOpeningEventRepository;
import com.shivang.crm.modules.dialer.service.CallOpeningEventService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultCallOpeningEventService implements CallOpeningEventService {

    private final CallOpeningEventRepository repo;

    @Override
    public CallOpeningEvent createEvent(UUID tenantId, UUID userId, String agentId, UUID callId, String externalCallId, String providerKey, String triggerKey, CallOpeningInstruction instruction) {
        CallOpeningEvent e = CallOpeningEvent.builder()
            .tenantId(tenantId)
            .userId(userId)
            .agentId(agentId)
            .callId(callId)
            .externalCallId(externalCallId)
            .providerKey(providerKey)
            .triggerKey(triggerKey)
            .instruction(instruction == null ? null : java.util.Map.of("instruction", instruction))
            .deliveryStatus("PENDING")
            .createdAt(Instant.now())
            .build();
        return repo.save(e);
    }

    @Override
    public List<CallOpeningEvent> findPendingForTenant(UUID tenantId) {
        return repo.findByTenantIdAndDeliveryStatus(tenantId, "PENDING");
    }

    @Override
    public List<CallOpeningEvent> findPendingForAgent(UUID tenantId, String agentId) {
        return repo.findByTenantIdAndAgentIdAndDeliveryStatus(tenantId, agentId, "PENDING");
    }

    @Override
    public void markDelivered(UUID eventId) {
        var opt = repo.findById(eventId);
        if (opt.isPresent()) {
            var e = opt.get();
            e.setDeliveryStatus("DELIVERED");
            e.setDeliveredAt(Instant.now());
            repo.save(e);
        }
    }
}
