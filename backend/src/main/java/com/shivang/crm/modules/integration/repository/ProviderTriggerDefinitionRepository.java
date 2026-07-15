package com.shivang.crm.modules.integration.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.integration.entity.ProviderTriggerDefinition;

@Repository
public interface ProviderTriggerDefinitionRepository extends JpaRepository<ProviderTriggerDefinition, UUID> {
    Optional<ProviderTriggerDefinition> findByProviderIdAndTriggerKey(UUID providerId, String triggerKey);
}
