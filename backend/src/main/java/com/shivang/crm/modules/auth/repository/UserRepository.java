package com.shivang.crm.modules.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.auth.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

        @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.email = :email")
        Optional<User> findByTenantIdAndEmail(@Param("tenantId") UUID tenantId, @Param("email") String email);

        @Query("SELECT u FROM User u WHERE u.email = :email")
        Optional<User> findByEmail(@Param("email") String email);


         Optional<User> findByIdAndTenantIdAndDeletedFalse(
                UUID id,
                UUID tenantId);
       

    @Query("SELECT u.id FROM User u WHERE u.tenantId = :tenantId AND u.managerId = :managerId")
    List<UUID> findTeamUserIdsByManagerAndTenant(@Param("tenantId") UUID tenantId,
                                                 @Param("managerId") UUID managerId);


        @Query("SELECT u FROM User u WHERE u.id = :id AND u.tenantId = :tenantId")
        Optional<User> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

        @Query("SELECT u FROM User u WHERE u.tenantId IS NULL AND u.email = :email")
        Optional<User> findPlatformUserByEmail(@Param("email") String email);

        @Query("SELECT u FROM User u WHERE u.tenantId IS NULL AND u.id = :id")
        Optional<User> findPlatformUserById(@Param("id") UUID id);

        boolean existsByTenantIdAndEmail(UUID tenantId, String email);

        boolean existsByTenantIdIsNullAndEmail(String email);

        Page<User> findByTenantIdIsNullAndEmailContainingIgnoreCase(String email,
                        Pageable pageable);

        Page<User> findByTenantIdIsNull(Pageable pageable);

        @Query("""
         SELECT u
         FROM User u
         JOIN Role r ON u.roleId = r.id
         WHERE u.tenantId = :tenantId
         AND r.name = 'MANAGER'
         AND u.isActive = true
      """)
      List<User> findManagers(UUID tenantId);

        Integer countByTenantId(UUID tenantId);

        // @Query("UPDATE User u SET u.isDeleted = true, u.isActive = false WHERE u.id =
        // :userId AND u.tenantId = :tenantId")
        // void softDelete(@Param("userId") UUID userId, @Param("tenantId") UUID
        // tenantId);

        Page<User> findByTenantIdAndEmailContainingIgnoreCase(UUID tenantId, String email,
                        Pageable pageable);

        Page<User> findByTenantId(UUID tenantId,
                        Pageable pageable);

        @Query("""
                            SELECT u
                            FROM User u
                            JOIN Tenant t ON t.id = u.tenantId
                            WHERE t.resellerId = :resellerId
                        """)
        Page<User> findByResellerId(
                        @Param("resellerId") UUID resellerId,
                        Pageable pageable);

        @Query("""
                            SELECT u
                            FROM User u
                            JOIN Tenant t ON t.id = u.tenantId
                            WHERE t.resellerId = :resellerId
                              AND (
                                    LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                                 OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                                 OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                              )
                        """)
        Page<User> findByResellerIdAndSearch(
                        @Param("resellerId") UUID resellerId,
                        @Param("search") String search,
                        Pageable pageable);

        Page<User> findAll(Pageable pageable);

        @Query("""
                            SELECT u
                            FROM User u
                            WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                               OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                               OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                        """)
        Page<User> searchAll(
                        @Param("search") String search,
                        Pageable pageable);
}
