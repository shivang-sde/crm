package com.shivang.crm.modules.rbac.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shivang.crm.modules.rbac.entity.UserRole;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    @Query("SELECT ur FROM UserRole ur JOIN FETCH ur.role WHERE ur.userId = :userId AND ur.tenantId = :tenantId")
    Optional<UserRole> findByUserIdAndTenantId(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
    
    @Query("SELECT ur FROM UserRole ur JOIN FETCH ur.role WHERE ur.userId = :userId AND ur.tenantId IS NULL")
    Optional<UserRole> findByUserIdAndTenantIdIsNull(@Param("userId") UUID userId);

    @Query("SELECT ur FROM UserRole ur JOIN FETCH ur.role WHERE ur.userId = :userId AND ur.tenantId IS NULL")
    Optional<UserRole> findPlatformRole(@Param("userId") UUID userId);
    
    @Query("SELECT ur FROM UserRole ur JOIN FETCH ur.role WHERE ur.userId = :userId")
    List<UserRole> findByUserId(@Param("userId") UUID userId);
    
    List<UserRole> findByRoleId(UUID roleId);
    
    void deleteByUserIdAndTenantId(UUID userId, UUID tenantId);

    void deleteByUserIdAndTenantIdIsNull(UUID userId);
}
