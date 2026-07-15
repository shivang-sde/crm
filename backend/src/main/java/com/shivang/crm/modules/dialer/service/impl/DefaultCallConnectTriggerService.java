package com.shivang.crm.modules.dialer.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.dialer.entity.CallConnectTrigger;
import com.shivang.crm.modules.dialer.repository.CallConnectTriggerRepository;
import com.shivang.crm.modules.dialer.service.CallConnectTriggerService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultCallConnectTriggerService implements CallConnectTriggerService {

    private final CallConnectTriggerRepository repo;

    @Override
    public CallConnectTrigger save(CallConnectTrigger trigger) {
        return repo.save(trigger);
    }

    @Override
    public java.util.Optional<CallConnectTrigger> findById(UUID id) {
        return repo.findById(id);
    }

    @Override
    public List<CallConnectTrigger> findByTenantId(UUID tenantId) {
        return repo.findByTenantId(tenantId);
    }

    @Override
    public List<CallConnectTrigger> findActiveByTenantAndDirection(UUID tenantId, String direction) {
        var all = repo.findByTenantId(tenantId);
        return all.stream()
            .filter(t -> Boolean.TRUE.equals(t.getIsActive()))
            .filter(t -> "BOTH".equalsIgnoreCase(t.getCallDirection()) || t.getCallDirection().equalsIgnoreCase(direction))
            .sorted((a,b) -> Integer.compare(a.getPriority() == null ? 0 : a.getPriority(), b.getPriority() == null ? 0 : b.getPriority()))
            .collect(Collectors.toList());
    }
}
