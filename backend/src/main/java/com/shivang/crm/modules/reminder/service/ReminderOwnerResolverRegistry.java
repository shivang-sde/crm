package com.shivang.crm.modules.reminder.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.reminder.entity.ReminderSourceType;

@Component
public class ReminderOwnerResolverRegistry {

    private final Map<ReminderSourceType, ReminderOwnerResolver> resolvers;

    public ReminderOwnerResolverRegistry(java.util.List<ReminderOwnerResolver> resolvers) {
        this.resolvers = resolvers.stream()
            .collect(Collectors.toMap(ReminderOwnerResolver::supportedType, r -> r));
        if (this.resolvers.size() != resolvers.size()) {
            throw new IllegalStateException("Duplicate ReminderOwnerResolver for the same source type");
        }
    }

    public Optional<UUID> resolveOwner(ReminderSourceType sourceType, UUID tenantId, UUID sourceId) {
        ReminderOwnerResolver resolver = resolvers.get(sourceType);
        if (resolver == null) {
            return Optional.empty();
        }
        return resolver.resolveOwner(tenantId, sourceId);
    }
}
