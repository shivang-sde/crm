package com.shivang.crm.modules.records.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shivang.crm.modules.records.entity.RecordType;

public interface RecordTypeRepository extends JpaRepository<RecordType, UUID> {

    Optional<RecordType> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    Optional<RecordType> findByTenantIdAndKeyAndDeletedFalse(UUID tenantId, String key);

    Optional<RecordType> findByTenantIdAndNameAndDeletedFalse(UUID tenantId, String name);

    boolean existsByTenantIdAndKeyAndDeletedFalse(UUID tenantId, String key);

    boolean existsByTenantIdAndNameAndDeletedFalse(UUID tenantId, String name);

    boolean existsByTenantIdAndKeyAndDeletedFalseAndIdNot(UUID tenantId, String key, UUID id);

    boolean existsByTenantIdAndNameAndDeletedFalseAndIdNot(UUID tenantId, String name, UUID id);

    Page<RecordType> findByTenantIdAndDeletedFalse(UUID tenantId, Pageable pageable);

    List<RecordType> findByTenantIdAndDeletedFalse(UUID tenantId);

    List<RecordType> findByTenantIdAndIsActiveTrueAndDeletedFalse(UUID tenantId);
}
