package com.shivang.crm.modules.acquisition.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.acquisition.mapping.LeadIngestionFieldMapping;

@Repository
public interface LeadIngestionFieldMappingRepository extends JpaRepository<LeadIngestionFieldMapping, UUID> {

    List<LeadIngestionFieldMapping> findByTenantIdAndIngestionConfigIdAndActiveTrueAndDeletedFalseOrderByDisplayOrderAsc(
        UUID tenantId,
        UUID ingestionConfigId
    );

    List<LeadIngestionFieldMapping> findByTenantIdAndIngestionConfigIdAndDeletedFalseOrderByDisplayOrderAscCreatedAtAsc(
        UUID tenantId,
        UUID ingestionConfigId
    );

    java.util.Optional<LeadIngestionFieldMapping> findByIdAndTenantIdAndIngestionConfigIdAndDeletedFalse(
        UUID id,
        UUID tenantId,
        UUID ingestionConfigId
    );

    Optional<LeadIngestionFieldMapping> findByTenantIdAndIngestionConfigIdAndTargetTypeAndTargetFieldAndDeletedFalse(
        UUID tenantId,
        UUID ingestionConfigId,
        com.shivang.crm.modules.acquisition.mapping.LeadIngestionTargetType targetType,
        String targetField
    );

    @Query("""
        SELECT m FROM LeadIngestionFieldMapping m
        WHERE m.tenantId = :tenantId
          AND m.ingestionConfigId = :ingestionConfigId
          AND m.targetType = :targetType
          AND m.targetField = :targetField
          AND m.deleted = false
          AND m.id <> :excludeId
    """)
    Optional<LeadIngestionFieldMapping> findDuplicateTargetExcludingId(
        @Param("tenantId") UUID tenantId,
        @Param("ingestionConfigId") UUID ingestionConfigId,
        @Param("targetType") com.shivang.crm.modules.acquisition.mapping.LeadIngestionTargetType targetType,
        @Param("targetField") String targetField,
        @Param("excludeId") UUID excludeId
    );
}