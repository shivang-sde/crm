package com.shivang.crm.modules.records.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shivang.crm.modules.records.entity.CrmRecord;

public interface CrmRecordRepository extends JpaRepository<CrmRecord, UUID> {

    Optional<CrmRecord> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    Optional<CrmRecord> findByIdAndTenantIdAndRecordTypeIdAndDeletedFalse(UUID id, UUID tenantId, UUID recordTypeId);

    Page<CrmRecord> findByTenantIdAndRecordTypeIdAndDeletedFalse(UUID tenantId, UUID recordTypeId, Pageable pageable);

    Page<CrmRecord> findByTenantIdAndDeletedFalse(UUID tenantId, Pageable pageable);

    long countByRecordTypeIdAndDeletedFalse(UUID recordTypeId);

    boolean existsByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);
}
