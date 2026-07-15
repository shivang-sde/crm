package com.shivang.crm.modules.integration.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.integration.entity.ProviderActionDefinition;

@Repository
public interface ProviderActionDefinitionRepository extends JpaRepository<ProviderActionDefinition, UUID> {
    Optional<ProviderActionDefinition> findByProviderIdAndActionKey(UUID providerId, String actionKey);
}
