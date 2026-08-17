package com.shivang.crm.modules.acquisition.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.acquisition.config.LeadIngestionConfig;

@Repository
public interface LeadIngestionConfigRepository extends JpaRepository<LeadIngestionConfig, UUID> {

    Optional<LeadIngestionConfig> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    Optional<LeadIngestionConfig> findByPublicKeyAndDeletedFalse(String publicKey);

    List<LeadIngestionConfig> findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(UUID tenantId);

    boolean existsByPublicKey(String publicKey);
}