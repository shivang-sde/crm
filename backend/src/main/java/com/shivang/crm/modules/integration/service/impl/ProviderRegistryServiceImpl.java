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
import com.shivang.crm.shared.exception.BusinessException;

@Service
public class ProviderRegistryServiceImpl implements ProviderRegistryService {

    private final ProviderDefinitionRepository providerRepository;
    private final ProviderActionDefinitionRepository actionRepository;
    private final ProviderTriggerDefinitionRepository triggerRepository;

    public ProviderRegistryServiceImpl(ProviderDefinitionRepository providerRepository,
                                       ProviderActionDefinitionRepository actionRepository,
                                       ProviderTriggerDefinitionRepository triggerRepository) {
        this.providerRepository = providerRepository;
        this.actionRepository = actionRepository;
        this.triggerRepository = triggerRepository;
    }

    @Override
    public Optional<ProviderDefinition> findByProviderKey(String providerKey) {
        return providerRepository.findByProviderKey(providerKey);
    }

    @Override
    public Optional<ProviderDefinition> findById(UUID id) {
        return providerRepository.findById(id);
    }

    @Override
    public ProviderDefinition save(ProviderDefinition providerDefinition) {
        return providerRepository.save(providerDefinition);
    }

    @Override
    public Optional<ProviderActionDefinition> findActionByProviderKeyAndActionKey(String providerKey, String actionKey) {
        return findByProviderKey(providerKey)
            .flatMap(provider -> actionRepository.findByProviderIdAndActionKey(provider.getId(), actionKey));
    }

    @Override
    public Optional<ProviderTriggerDefinition> findTriggerByProviderKeyAndTriggerKey(String providerKey, String triggerKey) {
        return findByProviderKey(providerKey)
            .flatMap(provider -> triggerRepository.findByProviderIdAndTriggerKey(provider.getId(), triggerKey));
    }

    @Override
    public void validateProviderActive(ProviderDefinition providerDefinition) {
        if (providerDefinition == null || !Boolean.TRUE.equals(providerDefinition.getIsActive())) {
            throw new BusinessException("PROVIDER_INACTIVE", "Provider is inactive");
        }
    }

    @Override
    public void validateActionActive(ProviderActionDefinition actionDefinition) {
        if (actionDefinition == null || !Boolean.TRUE.equals(actionDefinition.getIsActive())) {
            throw new BusinessException("ACTION_INACTIVE", "Action is inactive");
        }
    }

    @Override
    public void validateTriggerActive(ProviderTriggerDefinition triggerDefinition) {
        if (triggerDefinition == null || !Boolean.TRUE.equals(triggerDefinition.getIsActive())) {
            throw new BusinessException("TRIGGER_INACTIVE", "Trigger is inactive");
        }
    }
}
