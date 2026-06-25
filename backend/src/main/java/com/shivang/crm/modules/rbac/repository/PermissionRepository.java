package com.shivang.crm.modules.rbac.repository;

import com.shivang.crm.modules.rbac.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByModuleAndAction(String module, String action);

    List<Permission> findByModule(String module);

    boolean existsByModuleAndAction(String module, String action);
}
