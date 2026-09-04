package com.shivang.crm.modules.call.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.call.dto.CallingProviderOption;
import com.shivang.crm.modules.call.service.CallingProviderService;
import com.shivang.crm.modules.integration.entity.ProviderActionDefinition;
import com.shivang.crm.modules.integration.entity.ProviderDefinition;
import com.shivang.crm.modules.integration.repository.ConnectorInstanceRepository;
import com.shivang.crm.modules.integration.repository.ProviderActionDefinitionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CallingProviderServiceImpl implements CallingProviderService {

    private final ConnectorInstanceRepository connectorInstanceRepository;
    private final ProviderActionDefinitionRepository providerActionDefinitionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CallingProviderOption> getAvailableCallingProviders(UUID tenantId) {
        // ponytail: O(n) scan over tenant instances + per-instance action lookup; use indexed query/cache if tenant has many providers
        if (tenantId == null) return List.of();
        return connectorInstanceRepository.findByTenantId(tenantId).stream()
            .filter(instance -> Boolean.TRUE.equals(instance.getIsActive()))
            .filter(instance -> instance.getProvider() != null)
            .filter(instance -> {
                ProviderDefinition provider = instance.getProvider();
                if (!Boolean.TRUE.equals(provider.getIsActive())) return false;
                if (provider.getCategory() == null || !"CALLING".equalsIgnoreCase(provider.getCategory())) return false;
                Optional<ProviderActionDefinition> action =
                    providerActionDefinitionRepository.findByProviderIdAndActionKey(provider.getId(), "CLICK_TO_CALL");
                return action.isPresent() && Boolean.TRUE.equals(action.get().getIsActive());
            })
            .map(instance -> new CallingProviderOption(
                instance.getProvider().getProviderKey(),
                instance.getProvider().getProviderName(),
                instance.getId(),
                instance.getConnectorName(),
                instance.getEnvironment(),
                Boolean.TRUE.equals(instance.getIsActive())
            ))
            .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.providerName(), b.providerName()))
            .toList();
    }
}
