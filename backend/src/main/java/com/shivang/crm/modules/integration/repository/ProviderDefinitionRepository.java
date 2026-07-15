package com.shivang.crm.modules.integration.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.integration.entity.ProviderDefinition;

@Repository
public interface ProviderDefinitionRepository extends JpaRepository<ProviderDefinition, UUID> {
    Optional<ProviderDefinition> findByProviderKey(String providerKey);
}
