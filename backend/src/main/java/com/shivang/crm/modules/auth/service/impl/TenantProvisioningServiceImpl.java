package com.shivang.crm.modules.auth.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.auth.dto.request.TenantProvisionRequest;
import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.auth.service.TenantProvisioningService;
import com.shivang.crm.modules.rbac.config.DefaultRoleConfig;
import com.shivang.crm.modules.rbac.entity.Permission;
import com.shivang.crm.modules.rbac.entity.Role;
import com.shivang.crm.modules.rbac.entity.RolePermission;
import com.shivang.crm.modules.rbac.entity.UserRole;
import com.shivang.crm.modules.rbac.repository.PermissionRepository;
import com.shivang.crm.modules.rbac.repository.RolePermissionRepository;
import com.shivang.crm.modules.rbac.repository.RoleRepository;
import com.shivang.crm.modules.rbac.repository.UserRoleRepository;
import com.shivang.crm.modules.tenant.entity.Tenant;
import com.shivang.crm.modules.tenant.repository.TenantRepository;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.ResourceNotFoundException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TenantProvisioningServiceImpl implements TenantProvisioningService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final DefaultRoleConfig defaultRoleConfig;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public TenantProvisionResult provisionTenant(TenantProvisionRequest request, String authenticatedUserId) {
        UUID resolvedResellerId = resolveResellerId(request.getResellerId(), authenticatedUserId);

        Tenant tenant = Tenant.builder()
                .name(request.getCompanyName().trim())
                .slug(generateSlug(request.getCompanyName()))
                .planType("free")
                .maxUsers(request.getMaxUsers() != null ? request.getMaxUsers() : 5)
                .isActive(true)
                .companyEmail(request.getCompanyEmail().trim())
                .companyPhone(request.getCompanyPhone().trim())
                .website(request.getWebsite() == null ? "" : request.getWebsite().trim())
                .industry(request.getIndustry() == null ? "" : request.getIndustry().trim())
                .timezone(request.getTimezone() == null ? "" : request.getTimezone().trim())
                .currencyCode(request.getCurrencyCode() == null ? "" : request.getCurrencyCode().trim())
                .language(request.getLanguage() == null ? "" : request.getLanguage().trim())
                .addressLine1(request.getAddressLine1() == null ? "" : request.getAddressLine1().trim())
                .city(request.getCity() == null ? "" : request.getCity().trim())
                .state(request.getState() == null ? "" : request.getState().trim())
                .postalCode(request.getPostalCode() == null ? "" : request.getPostalCode().trim())
                .country(request.getCountry() == null ? "" : request.getCountry().trim())
                .logoUrl(request.getLogoUrl() == null ? "" : request.getLogoUrl().trim())
                .primaryColor(request.getPrimaryColor() == null ? "#007BFF" : request.getPrimaryColor().trim())
                .subscriptionEndDate(request.getSubscriptionEndDate())
                .resellerId(resolvedResellerId)
                .build();

        try {
            tenant = tenantRepository.save(tenant);
            // Flush to ensure the trigger fires and default roles are created
            entityManager.flush();
        } catch (DataIntegrityViolationException e) {
            tenant.setSlug(tenant.getSlug() + "-" + UUID.randomUUID().toString().substring(0, 8));
            try {
                tenant = tenantRepository.save(tenant);
                entityManager.flush(); // Flush again after retry
            } catch (Exception ex) {
                throw new BusinessException("TENANT_CREATION_FAILED", "Could not create tenant");
            }
        } catch (Exception e) {
            throw new BusinessException("TENANT_CREATION_FAILED", "Could not create tenant");
        }

        UUID tenantId = tenant.getId();

        // Try to find the ADMIN role, create if not found (fallback)
        Role adminRole = roleRepository.findByNameAndTenantId("ADMIN", tenantId)
                .orElseGet(
                        () -> {
                            log.warn("ADMIN role not found for tenant {}. Creating default roles manually.",
                                    tenantId);
                            return createDefaultRolesManually(tenantId);
                        });

        if (adminRole == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "Failed to create default ADMIN role for tenant");
        }

        createTenantAdmin(request.getAdmin(), tenant, adminRole);

        if (resolvedResellerId != null) {
            insertResellerTenantMapping(resolvedResellerId, tenant.getId());
        }

        User adminUser = userRepository
                .findByTenantIdAndEmail(tenant.getId(), request.getAdmin().getEmail().toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getAdmin().getEmail()));

        UserRole adminUserRole = userRoleRepository.findByUserIdAndTenantId(adminUser.getId(), tenant.getId())
                .orElseThrow(() -> new ResourceNotFoundException("UserRole", adminUser.getId().toString()));

        return new TenantProvisionResult(tenant, adminUser, adminUserRole);
    }

    private UUID resolveResellerId(UUID requestedResellerId, String authenticatedUserId) {
        if (authenticatedUserId == null) {
            if (requestedResellerId != null) {
                throw new BusinessException("FORBIDDEN", "Cannot assign reseller during public signup");
            }
            return null;
        }

        User authenticatedUser = userRepository.findById(UUID.fromString(authenticatedUserId))
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Authenticated user not found"));

        UserRole authenticatedUserRole = findUserRole(authenticatedUser)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Authenticated user role not found"));

        if (isReseller(authenticatedUserRole)) {
            return authenticatedUser.getId();
        }

        if (!isSuperAdmin(authenticatedUserRole)) {
            throw new BusinessException("FORBIDDEN", "Only SUPERADMIN or RESELLER may provision tenants");
        }

        if (requestedResellerId == null) {
            return null;
        }

        User reseller = userRepository.findById(requestedResellerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", requestedResellerId.toString()));

        UserRole resellerRole = findUserRole(reseller)
                .orElseThrow(() -> new BusinessException("INVALID_RESELLER", "Selected user is not a reseller"));

        if (!isReseller(resellerRole)) {
            throw new BusinessException("INVALID_RESELLER", "Selected user is not a reseller");
        }

        return requestedResellerId;
    }

    /**
     * Fallback method to create default roles manually if trigger fails
     * IMPORTANT: This logic must remain in sync with PostgreSQL trigger function:
     * 
     * @see database/migrations/V2__create_tenant_roles_trigger.sql
     */
    private Role createDefaultRolesManually(UUID tenantId) {
        try {
            log.info("Creating default roles manually for tenant {} using config", tenantId);

            // Create roles
            Role adminRole = createRole(tenantId, "ADMIN", "TENANT",
                    "Tenant administrator - full access to all tenant records");
            Role managerRole = createRole(tenantId, "MANAGER", "TENANT",
                    "Manager - access to team records");
            Role employeeRole = createRole(tenantId, "EMPLOYEE", "TENANT",
                    "Employee - own records only");

            // Batch collect all role permissions
            List<RolePermission> allRolePermissions = new ArrayList<>();
            List<Permission> allPermissions = permissionRepository.findAll();

            // Assign ADMIN permissions using config
            for (Permission perm : allPermissions) {
                if (defaultRoleConfig.isAdminPermission(perm)) {
                    allRolePermissions.add(createRolePermission(
                            adminRole.getId(), perm.getId(), "ALL"));
                }
            }

            // Assign MANAGER permissions using config
            for (Permission perm : allPermissions) {
                if (defaultRoleConfig.isManagerPermission(perm)) {
                    allRolePermissions.add(createRolePermission(
                            managerRole.getId(), perm.getId(), "TEAM"));
                }
            }

            // Assign EMPLOYEE permissions using config
            for (Permission perm : allPermissions) {
                if (defaultRoleConfig.isEmployeePermission(perm)) {
                    allRolePermissions.add(createRolePermission(
                            employeeRole.getId(), perm.getId(), "OWN"));
                }
            }

            // Batch save
            rolePermissionRepository.saveAll(allRolePermissions);

            log.info("Successfully created default roles manually for tenant {}. " +
                    "Created {} role permissions total.", tenantId, allRolePermissions.size());
            return adminRole;

        } catch (Exception e) {
            log.error("Failed to create default roles manually for tenant {}: {}",
                    tenantId, e.getMessage(), e);
            throw new BusinessException("ROLE_CREATION_FAILED",
                    "Failed to create default roles: " + e.getMessage());
        }
    }

    private Role createRole(UUID tenantId, String name, String level, String description) {
        Role role = new Role();
        role.setName(name);
        role.setLevel(level);
        role.setTenantId(tenantId);
        role.setDescription(description);
        return roleRepository.save(role);
    }

    private RolePermission createRolePermission(UUID roleId, UUID permissionId, String accessScope) {
        RolePermission rp = new RolePermission();
        rp.setRoleId(roleId);
        rp.setPermissionId(permissionId);
        rp.setAccessScope(accessScope);
        return rp;
    }

    private void createTenantAdmin(TenantProvisionRequest.TenantAdminRequest adminRequest, Tenant tenant,
            Role adminRole) {
        String adminEmail = adminRequest.getEmail().toLowerCase().trim();
        if (userRepository.existsByTenantIdAndEmail(tenant.getId(), adminEmail)) {
            throw new BusinessException("EMAIL_EXISTS", "Admin email already exists for this tenant");
        }

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setEmail(adminEmail);
        user.setPasswordHash(passwordEncoder.encode(adminRequest.getPassword()));
        user.setFirstName(adminRequest.getFirstName());
        user.setLastName(adminRequest.getLastName());
        user.setIsActive(true);
        user.setEmailVerified(false);
        user.setRoleId(adminRole.getId());

        user = userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(adminRole.getId());
        userRole.setTenantId(tenant.getId());
        userRoleRepository.save(userRole);
    }

    private void insertResellerTenantMapping(UUID resellerId, UUID tenantId) {
        entityManager
                .createNativeQuery(
                        "INSERT INTO reseller_tenants (reseller_user_id, tenant_id) VALUES (:resellerId, :tenantId)")
                .setParameter("resellerId", resellerId)
                .setParameter("tenantId", tenantId)
                .executeUpdate();
    }

    private String generateSlug(String companyName) {
        String baseSlug = companyName.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-");

        // Add timestamp to ensure uniqueness
        return baseSlug + "-" + System.currentTimeMillis();
    }

    private boolean isSuperAdmin(UserRole userRole) {
        return userRole != null && userRole.getTenantId() == null
                && "SUPERADMIN".equalsIgnoreCase(userRole.getRole().getName());
    }

    private boolean isReseller(UserRole userRole) {
        return userRole != null && userRole.getTenantId() == null
                && "RESELLER".equalsIgnoreCase(userRole.getRole().getName());
    }

    private java.util.Optional<UserRole> findUserRole(User user) {
        if (user.getTenantId() == null) {
            return userRoleRepository.findPlatformRole(user.getId());
        }

        return userRoleRepository.findByUserIdAndTenantId(user.getId(), user.getTenantId());
    }
}
