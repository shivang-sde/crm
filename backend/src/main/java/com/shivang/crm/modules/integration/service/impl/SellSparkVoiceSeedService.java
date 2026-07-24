package com.shivang.crm.modules.integration.service.impl;

import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.integration.entity.ProviderActionDefinition;
import com.shivang.crm.modules.integration.entity.ProviderDefinition;
import com.shivang.crm.modules.integration.entity.ProviderTriggerDefinition;
import com.shivang.crm.modules.integration.repository.ProviderActionDefinitionRepository;
import com.shivang.crm.modules.integration.repository.ProviderDefinitionRepository;
import com.shivang.crm.modules.integration.repository.ProviderTriggerDefinitionRepository;

@Service
public class SellSparkVoiceSeedService {

    private final ProviderDefinitionRepository providerRepository;
    private final ProviderActionDefinitionRepository actionRepository;
    private final ProviderTriggerDefinitionRepository triggerRepository;

    public SellSparkVoiceSeedService(ProviderDefinitionRepository providerRepository,
                                     ProviderActionDefinitionRepository actionRepository,
                                     ProviderTriggerDefinitionRepository triggerRepository) {
        this.providerRepository = providerRepository;
        this.actionRepository = actionRepository;
        this.triggerRepository = triggerRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        Optional<ProviderDefinition> existingProvider = providerRepository.findByProviderKey("sellspark_voice");
        ProviderDefinition provider = existingProvider.orElseGet(() -> providerRepository.save(ProviderDefinition.builder()
            .providerKey("sellspark_voice")
            .providerName("SellSpark Voice")
            .category("CALLING")
            .description("Provider-neutral seed for SellSpark Voice integration")
            .isActive(true)
            .defaultConfig(Map.of("baseUrl", "https://sellspark.com"))
            .build()));

        upsertAction(provider, "CLICK_TO_CALL", "Click to Call", "POST", "/DialConnect/clicktocall",
            Map.of("userId", "{{credential.userId}}", "password", "{{credential.password}}", "number", "{{input.phoneNumber}}", "leadId", "{{input.leadId}}"));
        upsertTrigger(provider, "CALL_CONNECT", "Call Connect");
        upsertTrigger(provider, "CDR", "Call Detail Record");
    }

    private void upsertAction(ProviderDefinition provider, String actionKey, String actionName, String method, String endpointTemplate,
                              Map<String, Object> requestTemplate) {
        ProviderActionDefinition action = actionRepository.findByProviderIdAndActionKey(provider.getId(), actionKey)
            .orElseGet(ProviderActionDefinition::new);
        action.setProvider(provider);
        action.setActionKey(actionKey);
        action.setActionName(actionName);
        action.setDescription("Seeded action for " + provider.getProviderName());
        action.setIsActive(true);
        action.setEndpointTemplate(endpointTemplate);
        action.setHttpMethod(method);
        action.setRequestTemplate(requestTemplate);
        actionRepository.save(action);
    }

    private void upsertTrigger(ProviderDefinition provider, String triggerKey, String triggerName) {
        ProviderTriggerDefinition trigger = triggerRepository.findByProviderIdAndTriggerKey(provider.getId(), triggerKey)
            .orElseGet(ProviderTriggerDefinition::new);
        trigger.setProvider(provider);
        trigger.setTriggerKey(triggerKey);
        trigger.setTriggerName(triggerName);
        trigger.setDescription("Seeded trigger for " + provider.getProviderName());
        trigger.setIsActive(true);
        triggerRepository.save(trigger);
    }
}
