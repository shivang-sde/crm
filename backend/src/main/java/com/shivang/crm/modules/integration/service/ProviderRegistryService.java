package com.shivang.crm.modules.integration.service;

import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.integration.entity.ProviderActionDefinition;
import com.shivang.crm.modules.integration.entity.ProviderDefinition;
import com.shivang.crm.modules.integration.entity.ProviderTriggerDefinition;

public interface ProviderRegistryService {
    Optional<ProviderDefinition> findByProviderKey(String providerKey);
    Optional<ProviderDefinition> findById(UUID id);
    ProviderDefinition save(ProviderDefinition providerDefinition);
    Optional<ProviderActionDefinition> findActionByProviderKeyAndActionKey(String providerKey, String actionKey);
    Optional<ProviderTriggerDefinition> findTriggerByProviderKeyAndTriggerKey(String providerKey, String triggerKey);
    void validateProviderActive(ProviderDefinition providerDefinition);
    void validateActionActive(ProviderActionDefinition actionDefinition);
    void validateTriggerActive(ProviderTriggerDefinition triggerDefinition);
}
