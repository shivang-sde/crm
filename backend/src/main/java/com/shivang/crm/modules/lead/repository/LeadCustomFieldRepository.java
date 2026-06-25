package com.shivang.crm.modules.lead.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.lead.entity.LeadCustomField;

@Repository
public interface LeadCustomFieldRepository extends JpaRepository<LeadCustomField, UUID> {

    @Query("SELECT lcf FROM LeadCustomField lcf WHERE lcf.tenantId = :tenantId AND lcf.isActive = true ORDER BY lcf.displayOrder ASC")
    List<LeadCustomField> findActiveFieldsByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT lcf FROM LeadCustomField lcf WHERE lcf.tenantId = :tenantId ORDER BY lcf.displayOrder ASC")
    List<LeadCustomField> findByTenantIdOrderByDisplayOrder(@Param("tenantId") UUID tenantId);

    @Query("SELECT lcf FROM LeadCustomField lcf WHERE lcf.tenantId = :tenantId AND lcf.fieldKey = :fieldKey")
    Optional<LeadCustomField> findByTenantIdAndFieldKey(@Param("tenantId") UUID tenantId, @Param("fieldKey") String fieldKey);

    @Query("SELECT COUNT(lcf) FROM LeadCustomField lcf WHERE lcf.tenantId = :tenantId")
    Integer countByTenantId(@Param("tenantId") UUID tenantId);
}
