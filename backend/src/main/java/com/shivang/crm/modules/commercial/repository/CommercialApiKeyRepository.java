package com.shivang.crm.modules.commercial.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.commercial.entity.CommercialApiKey;

@Repository
public interface CommercialApiKeyRepository extends JpaRepository<CommercialApiKey, UUID> {

    Optional<CommercialApiKey> findByTenantId(UUID tenantId);

    Optional<CommercialApiKey> findByKeyPrefixAndIsActiveTrue(String keyPrefix);

    // Lookup by hash (primary validation path)
    Optional<CommercialApiKey> findByKeyHashAndIsActiveTrue(String keyHash);
}
