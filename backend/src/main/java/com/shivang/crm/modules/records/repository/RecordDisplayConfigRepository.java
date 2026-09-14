package com.shivang.crm.modules.records.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shivang.crm.modules.records.entity.RecordDisplayConfig;

public interface RecordDisplayConfigRepository extends JpaRepository<RecordDisplayConfig, UUID> {

    Optional<RecordDisplayConfig> findByRecordTypeIdAndTenantIdAndDeletedFalse(UUID recordTypeId, UUID tenantId);

    Optional<RecordDisplayConfig> findByRecordTypeIdAndDeletedFalse(UUID recordTypeId);
}
