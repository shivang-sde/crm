package com.shivang.crm.modules.records.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shivang.crm.modules.records.entity.RecordField;

public interface RecordFieldRepository extends JpaRepository<RecordField, UUID> {

    Optional<RecordField> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    Optional<RecordField> findByIdAndRecordTypeIdAndTenantIdAndDeletedFalse(UUID id, UUID recordTypeId, UUID tenantId);

    boolean existsByRecordTypeIdAndFieldKeyAndDeletedFalse(UUID recordTypeId, String fieldKey);

    boolean existsByRecordTypeIdAndFieldKeyAndDeletedFalseAndIdNot(UUID recordTypeId, String fieldKey, UUID id);

    @Query("SELECT f FROM RecordField f WHERE f.recordTypeId = :recordTypeId AND f.tenantId = :tenantId AND f.deleted = false ORDER BY f.displayOrder ASC, f.fieldKey ASC")
    List<RecordField> findByRecordTypeIdAndTenantIdAndDeletedFalseOrderByDisplayOrder(@Param("recordTypeId") UUID recordTypeId, @Param("tenantId") UUID tenantId);

    @Query("SELECT f FROM RecordField f WHERE f.recordTypeId = :recordTypeId AND f.tenantId = :tenantId AND f.deleted = false AND f.isActive = true ORDER BY f.displayOrder ASC, f.fieldKey ASC")
    List<RecordField> findActiveByRecordTypeIdAndTenantId(@Param("recordTypeId") UUID recordTypeId, @Param("tenantId") UUID tenantId);

    List<RecordField> findByTenantIdAndDeletedFalse(UUID tenantId);
}
