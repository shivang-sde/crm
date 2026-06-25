package com.shivang.crm.modules.rbac.repository;

import com.shivang.crm.modules.rbac.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    @Query("SELECT rp FROM RolePermission rp " +
            "JOIN FETCH rp.permission " +
            "WHERE rp.roleId = :roleId")
    List<RolePermission> findByRoleId(@Param("roleId") UUID roleId);

    @Query("SELECT rp FROM RolePermission rp WHERE rp.roleId IN :roleIds")
    List<RolePermission> findByRoleIds(@Param("roleIds") List<UUID> roleIds);

    @Query("SELECT rp FROM RolePermission rp " +
            "JOIN FETCH rp.permission " +
            "WHERE rp.roleId = :roleId AND rp.permissionId = :permissionId")
    Optional<RolePermission> findByRoleIdAndPermissionId(
            @Param("roleId") UUID roleId,
            @Param("permissionId") UUID permissionId);
}
