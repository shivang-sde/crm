package com.shivang.crm.modules.integration.service.impl;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.integration.entity.ProviderActionDefinition;
import com.shivang.crm.modules.integration.entity.ProviderDefinition;
import com.shivang.crm.modules.integration.entity.ProviderTriggerDefinition;
import com.shivang.crm.modules.integration.repository.ProviderActionDefinitionRepository;
import com.shivang.crm.modules.integration.repository.ProviderDefinitionRepository;
import com.shivang.crm.modules.integration.repository.ProviderTriggerDefinitionRepository;
import com.shivang.crm.modules.integration.service.ProviderRegistryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultProviderRegistryService implements ProviderRegistryService {

    private final ProviderDefinitionRepository providerDefinitionRepository;
    private final ProviderActionDefinitionRepository providerActionDefinitionRepository;
    private final ProviderTriggerDefinitionRepository providerTriggerDefinitionRepository;

    @Override
    public Optional<ProviderDefinition> findByProviderKey(String providerKey) {
        return providerDefinitionRepository.findByProviderKey(providerKey);
    }

    @Override
    public Optional<ProviderDefinition> findById(UUID id) {
        return providerDefinitionRepository.findById(id);
    }

    @Override
    public ProviderDefinition save(ProviderDefinition providerDefinition) {
        return providerDefinitionRepository.save(providerDefinition);
    }

    @Override
    public Optional<ProviderActionDefinition> findActionByProviderKeyAndActionKey(String providerKey, String actionKey) {
        return findByProviderKey(providerKey)
            .flatMap(provider -> providerActionDefinitionRepository.findByProviderIdAndActionKey(provider.getId(), actionKey));
    }

    @Override
    public Optional<ProviderTriggerDefinition> findTriggerByProviderKeyAndTriggerKey(String providerKey, String triggerKey) {
        return findByProviderKey(providerKey)
            .flatMap(provider -> providerTriggerDefinitionRepository.findByProviderIdAndTriggerKey(provider.getId(), triggerKey));
    }

    @Override
    public void validateProviderActive(ProviderDefinition providerDefinition) {
        if (providerDefinition == null || !Boolean.TRUE.equals(providerDefinition.getIsActive())) {
            throw new IllegalArgumentException("Provider is inactive");
        }
    }

    @Override
    public void validateActionActive(ProviderActionDefinition actionDefinition) {
        if (actionDefinition == null || !Boolean.TRUE.equals(actionDefinition.getIsActive())) {
            throw new IllegalArgumentException("Action is inactive");
        }
    }

    @Override
    public void validateTriggerActive(ProviderTriggerDefinition triggerDefinition) {
        if (triggerDefinition == null || !Boolean.TRUE.equals(triggerDefinition.getIsActive())) {
            throw new IllegalArgumentException("Trigger is inactive");
        }
    }
}
