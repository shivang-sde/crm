package com.shivang.crm.modules.contact.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shivang.crm.modules.contact.entity.ContactCustomField;

public interface ContactCustomFieldRepository extends JpaRepository<ContactCustomField, UUID> {

    java.util.Optional<ContactCustomField> findByIdAndTenantId(java.util.UUID id, java.util.UUID tenantId);

    @Query("SELECT field FROM ContactCustomField field WHERE field.tenantId = :tenantId AND field.isActive = true ORDER BY field.displayOrder ASC")
    List<ContactCustomField> findActiveFieldsByTenant(@Param("tenantId") UUID tenantId);

    List<ContactCustomField> findByTenantIdOrderByDisplayOrder(UUID tenantId);
}
