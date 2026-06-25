package com.shivang.crm.modules.lead.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.lead.entity.Lead;

@Repository
public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    Page<Lead> findByTenantId(
        UUID tenantId,
        Pageable pageable
);

    @Query("SELECT l FROM Lead l WHERE l.id = :id AND l.tenantId = :tenantId")
    Optional<Lead> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    Optional<Lead> findByTenantIdAndEmailAndIdNot(
        UUID tenantId,
        String email,
        UUID leadId
);

Optional<Lead> findByTenantIdAndPhoneAndIdNot(
        UUID tenantId,
        String phone,
        UUID leadId
);

    // Optional<Lead> findActiveLeadByEmailAndTenant(String email, UUID tenantId);
    // Optional<Lead> findActiveLeadByPhoneAndTenant(String phone, UUID tenantId);

    @Query("SELECT l FROM Lead l WHERE l.tenantId = :tenantId AND lower(l.email) = :email AND l.isConverted = false")
    Optional<Lead> findActiveLeadByEmailAndTenant(@Param("email") String email, @Param("tenantId") UUID tenantId);

    @Query("SELECT l FROM Lead l WHERE l.tenantId = :tenantId AND l.phone = :phone AND l.isConverted = false")
    Optional<Lead> findActiveLeadByPhoneAndTenant(@Param("phone") String phone, @Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.tenantId = :tenantId AND l.status.id = :statusId")
    Integer countByStatusIdAndTenant(@Param("statusId") UUID statusId, @Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.tenantId = :tenantId AND l.ownerId = :ownerUserId")
    Integer countByOwnerUserIdAndTenant(@Param("ownerUserId") UUID ownerUserId, @Param("tenantId") UUID tenantId);

    List<Lead> findByTenantIdAndIsConvertedFalseOrderByCreatedAtDesc(
        UUID tenantId,
        Pageable pageable
    );
}
