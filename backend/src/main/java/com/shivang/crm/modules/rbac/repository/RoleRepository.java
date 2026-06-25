package com.shivang.crm.modules.rbac.repository;

import com.shivang.crm.modules.rbac.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    
    Optional<Role> findByNameAndTenantId(String name, UUID tenantId);
    
    List<Role> findByTenantId(UUID tenantId);
    
    List<Role> findByLevel(String level);
    
    @Query("SELECT r FROM Role r WHERE r.tenantId = :tenantId OR (r.level = 'PLATFORM' AND r.name IN ('SUPERADMIN', 'RESELLER'))")
    List<Role> findAllAccessibleRoles(@Param("tenantId") UUID tenantId);
}
