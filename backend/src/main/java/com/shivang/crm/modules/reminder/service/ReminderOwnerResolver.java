package com.shivang.crm.modules.reminder.service;

import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.reminder.entity.ReminderSourceType;

public interface ReminderOwnerResolver {

    ReminderSourceType supportedType();

    Optional<UUID> resolveOwner(UUID tenantId, UUID sourceId);
}
