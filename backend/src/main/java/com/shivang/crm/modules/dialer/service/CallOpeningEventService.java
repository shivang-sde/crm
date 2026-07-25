package com.shivang.crm.modules.dialer.service;

import java.util.List;
import java.util.UUID;

import com.shivang.crm.modules.dialer.dto.CallOpeningInstruction;
import com.shivang.crm.modules.dialer.entity.CallOpeningEvent;

public interface CallOpeningEventService {
    CallOpeningEvent createEvent(UUID tenantId, UUID userId, String agentId, java.util.UUID callId, String externalCallId, String providerKey, String triggerKey, CallOpeningInstruction instruction);
    List<CallOpeningEvent> findPendingForAgent(UUID tenantId, String agentId);

    List<CallOpeningEvent> findPendingForTenantAndUser(UUID tenantId, UUID userId);

     CallOpeningEvent markDelivered(UUID tenantId, UUID userId, UUID eventId);
}
