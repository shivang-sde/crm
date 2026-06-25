package com.shivang.crm.modules.tenant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.tenant.entity.Tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    // Get all tenants (for SUPERADMIN)
    List<Tenant> findAllByOrderByCreatedAtDesc();

    // Get tenants for a specific reseller
    List<Tenant> findByResellerIdOrderByCreatedAtDesc(UUID resellerId);

    @Query("SELECT t FROM Tenant t LEFT JOIN FETCH t.reseller ORDER BY t.createdAt DESC")
    List<Tenant> findAllWithReseller();

    @Query("SELECT t FROM Tenant t LEFT JOIN FETCH t.reseller WHERE t.resellerId = :resellerId ORDER BY t.createdAt DESC")
    List<Tenant> findByResellerIdWithReseller(@Param("resellerId") UUID resellerId);
}
