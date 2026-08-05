package com.shivang.crm.modules.reminder.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.reminder.entity.ReminderSourceType;
import com.shivang.crm.modules.call.repository.CallRepository;

@Component
public class CallReminderOwnerResolver implements ReminderOwnerResolver {

    private final CallRepository callRepository;

    public CallReminderOwnerResolver(CallRepository callRepository) {
        this.callRepository = callRepository;
    }

    @Override
    public ReminderSourceType supportedType() {
        return ReminderSourceType.CALL;
    }

    @Override
    public Optional<UUID> resolveOwner(UUID tenantId, UUID sourceId) {
        return callRepository.findOwnerIdForReminder(sourceId, tenantId);
    }
}
