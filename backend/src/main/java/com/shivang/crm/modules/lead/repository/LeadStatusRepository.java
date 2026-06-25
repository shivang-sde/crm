package com.shivang.crm.modules.lead.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.lead.entity.LeadStatus;

@Repository
public interface LeadStatusRepository extends JpaRepository<LeadStatus, UUID> {

    Optional<LeadStatus> findByIdAndTenantId( UUID id,  UUID tenantId);

    @Query("SELECT ls FROM LeadStatus ls WHERE ls.tenantId = :tenantId ORDER BY ls.displayOrder ASC")
    List<LeadStatus> findByTenantIdOrderByDisplayOrder(@Param("tenantId") UUID tenantId);

    @Query("SELECT ls FROM LeadStatus ls WHERE ls.tenantId = :tenantId AND ls.isDefault = true")
    Optional<LeadStatus> findDefaultStatusByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT ls FROM LeadStatus ls WHERE ls.tenantId = :tenantId AND ls.name = :name")
    Optional<LeadStatus> findByTenantIdAndName(@Param("tenantId") UUID tenantId, @Param("name") String name);

    @Query("SELECT COUNT(ls) FROM LeadStatus ls WHERE ls.tenantId = :tenantId")
    Integer countByTenantId(@Param("tenantId") UUID tenantId);
}
