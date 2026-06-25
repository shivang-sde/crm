package com.shivang.crm.modules.lead.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.lead.entity.LeadSource;

@Repository
public interface LeadSourceRepository extends JpaRepository<LeadSource, UUID> {

    @Query("SELECT ls FROM LeadSource ls WHERE ls.tenantId = :tenantId AND ls.isActive = true ORDER BY ls.name ASC")
    List<LeadSource> findActiveSourcesByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT ls FROM LeadSource ls WHERE ls.tenantId = :tenantId AND ls.name = :name")
    Optional<LeadSource> findByTenantIdAndName(@Param("tenantId") UUID tenantId, @Param("name") String name);

    @Query("SELECT COUNT(ls) FROM LeadSource ls WHERE ls.tenantId = :tenantId")
    Integer countByTenantId(@Param("tenantId") UUID tenantId);
}
