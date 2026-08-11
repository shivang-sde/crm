package com.shivang.crm.modules.demo.repository;

import com.shivang.crm.modules.demo.entity.TenantDemoInstallation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantDemoInstallationRepository extends JpaRepository<TenantDemoInstallation, UUID> {
    
    Optional<TenantDemoInstallation> findByTenantIdAndTemplateKeyAndTemplateVersion(
            UUID tenantId, String templateKey, Integer templateVersion);
}
