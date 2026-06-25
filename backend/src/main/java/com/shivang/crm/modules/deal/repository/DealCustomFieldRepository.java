package com.shivang.crm.modules.deal.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.deal.entity.DealCustomField;

@Repository
public interface DealCustomFieldRepository extends JpaRepository<DealCustomField, UUID> {

    @Query("SELECT dcf FROM DealCustomField dcf WHERE dcf.tenantId = :tenantId AND dcf.isActive = true ORDER BY dcf.displayOrder ASC")
    List<DealCustomField> findActiveFieldsByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT dcf FROM DealCustomField dcf WHERE dcf.tenantId = :tenantId ORDER BY dcf.displayOrder ASC")
    List<DealCustomField> findByTenantIdOrderByDisplayOrder(@Param("tenantId") UUID tenantId);

    @Query("SELECT dcf FROM DealCustomField dcf WHERE dcf.tenantId = :tenantId AND dcf.fieldKey = :fieldKey")
    Optional<DealCustomField> findByTenantIdAndFieldKey(@Param("tenantId") UUID tenantId, @Param("fieldKey") String fieldKey);
}
