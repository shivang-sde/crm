package com.shivang.crm.modules.commercial.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.commercial.entity.IntegrationExternalRecord;

@Repository
public interface IntegrationExternalRecordRepository extends JpaRepository<IntegrationExternalRecord, UUID> {

    Optional<IntegrationExternalRecord> findByTenantIdAndExternalTypeAndExternalId(
            UUID tenantId, String externalType, String externalId);

    boolean existsByTenantIdAndExternalTypeAndExternalId(
            UUID tenantId, String externalType, String externalId);
}
