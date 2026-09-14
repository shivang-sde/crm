package com.shivang.crm.modules.records.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shivang.crm.modules.records.entity.RecordMappingProfile;

public interface RecordMappingProfileRepository extends JpaRepository<RecordMappingProfile, UUID> {

    Optional<RecordMappingProfile> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    Optional<RecordMappingProfile> findByTenantIdAndMappingKeyAndDeletedFalse(UUID tenantId, String mappingKey);

    boolean existsByTenantIdAndMappingKeyAndDeletedFalse(UUID tenantId, String mappingKey);

    boolean existsByTenantIdAndMappingKeyAndDeletedFalseAndIdNot(UUID tenantId, String mappingKey, UUID id);

    Page<RecordMappingProfile> findByTenantIdAndDeletedFalse(UUID tenantId, Pageable pageable);

    Page<RecordMappingProfile> findByTenantIdAndRecordTypeIdAndDeletedFalse(UUID tenantId, UUID recordTypeId, Pageable pageable);

    List<RecordMappingProfile> findByTenantIdAndDeletedFalse(UUID tenantId);

    List<RecordMappingProfile> findByTenantIdAndRecordTypeIdAndDeletedFalse(UUID tenantId, UUID recordTypeId);
}
