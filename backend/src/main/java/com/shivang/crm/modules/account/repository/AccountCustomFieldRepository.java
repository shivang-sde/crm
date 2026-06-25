package com.shivang.crm.modules.account.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shivang.crm.modules.account.entity.AccountCustomField;

public interface AccountCustomFieldRepository extends JpaRepository<AccountCustomField, UUID> {

    @Query("SELECT field FROM AccountCustomField field WHERE field.tenantId = :tenantId AND field.isActive = true ORDER BY field.displayOrder ASC")
    List<AccountCustomField> findActiveFieldsByTenant(@Param("tenantId") UUID tenantId);

    List<AccountCustomField> findByTenantIdOrderByDisplayOrder(UUID tenantId);
}
