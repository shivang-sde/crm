package com.shivang.crm.modules.commercial.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.commercial.entity.IntegrationExternalRecord;

@Repository
public interface IntegrationExternalRecordRepository extends JpaRepository<IntegrationExternalRecord, UUID> {

    Optional<IntegrationExternalRecord> findByTenantIdAndExternalTypeAndExternalId(
            UUID tenantId, String externalType, String externalId);

    boolean existsByTenantIdAndExternalTypeAndExternalId(
            UUID tenantId, String externalType, String externalId);

    @Query("SELECT r FROM IntegrationExternalRecord r WHERE r.tenantId = :tenantId AND r.accountId = :accountId AND r.externalType IN :types ORDER BY r.externalUpdatedAt DESC NULLS LAST, r.id DESC")
    Page<IntegrationExternalRecord> findByTenantAndAccountAndTypes(
            @Param("tenantId") UUID tenantId,
            @Param("accountId") UUID accountId,
            @Param("types") java.util.List<String> types,
            Pageable pageable);

    @Query("SELECT r FROM IntegrationExternalRecord r WHERE r.tenantId = :tenantId AND r.contactId = :contactId AND r.externalType IN :types ORDER BY r.externalUpdatedAt DESC NULLS LAST, r.id DESC")
    Page<IntegrationExternalRecord> findByTenantAndContactAndTypes(
            @Param("tenantId") UUID tenantId,
            @Param("contactId") UUID contactId,
            @Param("types") java.util.List<String> types,
            Pageable pageable);
}
