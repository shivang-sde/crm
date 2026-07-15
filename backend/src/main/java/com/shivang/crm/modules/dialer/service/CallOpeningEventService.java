package com.shivang.crm.modules.dialer.service;

import java.util.List;
import java.util.UUID;

import com.shivang.crm.modules.dialer.entity.CallOpeningEvent;
import com.shivang.crm.modules.dialer.dto.CallOpeningInstruction;

public interface CallOpeningEventService {
    CallOpeningEvent createEvent(UUID tenantId, UUID userId, String agentId, java.util.UUID callId, String externalCallId, String providerKey, String triggerKey, CallOpeningInstruction instruction);
    List<CallOpeningEvent> findPendingForTenant(UUID tenantId);
    List<CallOpeningEvent> findPendingForAgent(UUID tenantId, String agentId);
    void markDelivered(UUID eventId);
}
