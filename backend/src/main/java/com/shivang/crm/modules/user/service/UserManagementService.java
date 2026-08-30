package com.shivang.crm.modules.user.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.rbac.entity.Role;
import com.shivang.crm.modules.rbac.entity.UserRole;
import com.shivang.crm.modules.rbac.repository.RoleRepository;
import com.shivang.crm.modules.rbac.repository.UserRoleRepository;
import com.shivang.crm.modules.tenant.entity.Tenant;
import com.shivang.crm.modules.tenant.repository.TenantRepository;
import com.shivang.crm.modules.user.dto.request.CreateUserRequest;
import com.shivang.crm.modules.user.dto.request.UpdateUserRequest;
import com.shivang.crm.modules.user.dto.response.UserResponse;
import com.shivang.crm.modules.user.mapper.UserManagementMapper;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserManagementService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantContext tenantContext;
    private final TenantRepository tenantRepository;
    private final UserManagementMapper userManagementMapper;
    private final com.shivang.crm.modules.rbac.service.PermissionCacheEvictor permissionCacheEvictor;

    public Page<UserResponse> getUsers(
            UUID currentUserId,
            String role,
            String search,
            Pageable pageable) {

        Page<User> users;

        log.info("Current User={}", currentUserId);
        log.info("Role={}", role);

        if ("SUPERADMIN".equals(role)) {

            users = search == null || search.isBlank()
                    ? userRepository.findAll(pageable)
                    : userRepository.searchAll(search, pageable);

        } else if ("RESELLER".equals(role)) {

            users = search == null || search.isBlank()
                    ? userRepository.findByResellerId(
                            currentUserId,
                            pageable)
                    : userRepository.findByResellerIdAndSearch(
                            currentUserId,
                            search,
                            pageable);

        } else {


            if (!tenantContext.hasTenant()) {
                return Page.empty(pageable);
            }

        

            users = search == null || search.isBlank()
                    ? userRepository.findByTenantId(
                            tenantContext.getTenantId(),
                            pageable)
                    : userRepository.findByTenantIdAndEmailContainingIgnoreCase(
                            tenantContext.getTenantId(),
                            search,
                            pageable);
        }

        return users.map(this::toUserResponse);
    }

    public List<UserResponse> getManagers() {
        Optional<UUID> tenantId = parseTenantId();
        if (!tenantId.isPresent()) {
            return List.of();
        }
        return userRepository.findManagers(tenantId.get())
                .stream()
                .map(this::toUserResponse)
                .toList();
    }

    public List<UUID> getTeamUserIds(UUID userId, UUID tenantId) {
        return userRepository.findTeamUserIdsByManagerAndTenant(tenantId, userId);
    }

    public UserResponse getUser(UUID userId) {
        User user = resolveAccessibleUser(userId);
        return toUserResponse(user);
    }

    public UserResponse createUser(CreateUserRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException("UNAUTHORIZED", "Authenticated user is required");
        }

        UUID currentTenantId = parseTenantId().orElse(null);
        UUID currentUserId = UUID.fromString((String) authentication.getPrincipal());
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Authenticated user not found"));
        UserRole currentUserRole = findUserRole(currentUser)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Authenticated user role not found"));

        Role targetRole = request.getRoleId() != null
                ? roleRepository.findById(request.getRoleId())
                        .orElseThrow(() -> new ResourceNotFoundException("Role", request.getRoleId().toString()))
                : null;

        if (currentTenantId != null) {
            // Tenant admin / tenant context users can only create tenant-scoped users.
            if (!isTenantAdmin(currentUserRole)) {
                throw new BusinessException("FORBIDDEN", "Only tenant administrators may create tenant users");
            }

            if (request.getTenantId() != null && !request.getTenantId().equals(currentTenantId)) {
                throw new BusinessException("INVALID_REQUEST",
                        "Tenant ID may not be supplied when creating users inside a tenant context");
            }

            if (targetRole == null) {
                targetRole = roleRepository.findByNameAndTenantId("EMPLOYEE", currentTenantId)
                        .orElseThrow(() -> new BusinessException("ROLE_NOT_FOUND", "Default EMPLOYEE role not found"));
            }

            if (isPlatformRole(targetRole) || !currentTenantId.equals(targetRole.getTenantId())) {
                throw new BusinessException("FORBIDDEN", "Tenant administrators can only assign tenant-scoped roles");
            }

            Tenant tenant = tenantRepository.findById(currentTenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant", currentTenantId.toString()));

            Integer currentUsersCount = userRepository.countByTenantId(currentTenantId);
            if (tenant.getMaxUsers() != null && currentUsersCount >= tenant.getMaxUsers()) {

                throw new BusinessException(
                        "USER_LIMIT_REACHED",
                        String.format(
                                "Maximum allowed users (%d) reached for this tenant",
                                tenant.getMaxUsers()));
            }

            createTenantUser(request, targetRole, currentTenantId);
        } else {
            // Platform user context
            if (!isSuperAdmin(currentUserRole)) {
                throw new BusinessException("FORBIDDEN", "Only SUPERADMIN may create users from the platform context");
            }

            if (targetRole == null) {
                throw new BusinessException("ROLE_REQUIRED", "Role is required when creating platform users");
            }

            if (isPlatformRole(targetRole)) {
                createPlatformUser(request, targetRole);
            } else {
                createTenantScopedUserAsSuperAdmin(request, targetRole);
            }
        }

        User user = findCreatedUser(request.getEmail(), targetRole.getTenantId());
        return toUserResponse(user);
    }

    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        Optional<UUID> currentTenantId = parseTenantId();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException("UNAUTHORIZED", "Authenticated user is required");
        }
        UUID currentUserId = UUID.fromString((String) authentication.getPrincipal());
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Authenticated user not found"));
        UserRole currentUserRole = findUserRole(currentUser)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Authenticated user role not found"));

        User user = resolveAccessibleUser(userId);

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }
        if (request.getManagerId() != null) {
            user.setManagerId(request.getManagerId());
        }

        if (request.getRoleId() != null) {
            Role targetRole = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Role", request.getRoleId().toString()));

            if (currentTenantId.isPresent() && !isTenantAdmin(currentUserRole)) {
                throw new BusinessException("FORBIDDEN", "Only tenant administrators may update tenant user roles");
            }

            if (currentTenantId.isPresent()) {
                if (isPlatformRole(targetRole) || !currentTenantId.get().equals(targetRole.getTenantId())) {
                    throw new BusinessException("FORBIDDEN",
                            "Tenant administrators cannot assign platform or cross-tenant roles");
                }
            } else if (!isSuperAdmin(currentUserRole)) {
                throw new BusinessException("FORBIDDEN", "Only SUPERADMIN may update platform-context user roles");
            }

            user.setRoleId(targetRole.getId());
            userRepository.save(user);

            UserRole userRole;

            if (user.getTenantId() == null) {
                userRole = userRoleRepository
                        .findByUserIdAndTenantIdIsNull(userId)
                        .orElseThrow(() -> new RuntimeException("User role not found"));
            } else {
                userRole = userRoleRepository
                        .findByUserIdAndTenantId(userId, user.getTenantId())
                        .orElseThrow(() -> new RuntimeException("User role not found"));
            }

            userRole.setRoleId(targetRole.getId());

            userRoleRepository.save(userRole);

            // RBAC-8: role assignment changed -> evict this user's cache.
            permissionCacheEvictor.evictUserAfterCommit(userId, user.getTenantId());
        } else {
            userRepository.save(user);
        }

        return toUserResponse(user);
    }

    public void activateUser(UUID userId, boolean active) {
        User user = resolveAccessibleUser(userId);

        user.setIsActive(active);
        userRepository.save(user);
    }

    public void deleteUser(UUID userId) {
        User user = resolveAccessibleUser(userId);

        userRepository.delete(user);
    }

    /**
     * AN-6: Resolves a target user strictly within the caller's authority.
     * Tenant context: the user must belong to the caller's tenant (pre-existing
     * isolation). Platform context: SUPERADMIN is unrestricted; RESELLER may only
     * reach tenant users whose tenant lists the reseller as its reseller
     * (tenants.reseller_id == caller id). Authority always comes from the data
     * model, never from client-supplied ids or role names.
     */
    private User resolveAccessibleUser(UUID targetUserId) {
        UUID currentTenantId = parseTenantId().orElse(null);

        if (currentTenantId != null) {
            return userRepository.findByIdAndTenantId(targetUserId, currentTenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId.toString()));
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId.toString()));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException("UNAUTHORIZED", "Authenticated user is required");
        }
        UUID currentUserId = UUID.fromString((String) authentication.getPrincipal());
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Authenticated user not found"));
        UserRole currentUserRole = findUserRole(currentUser)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Authenticated user role not found"));

        if (!isSuperAdmin(currentUserRole)) {
            UUID targetTenantId = user.getTenantId();
            if (targetTenantId == null
                    || !currentUserId.equals(tenantRepository.findById(targetTenantId)
                            .map(Tenant::getResellerId)
                            .orElse(null))) {
                throw new BusinessException("FORBIDDEN", "You do not have access to this user");
            }
        }

        return user;
    }

    private UserResponse toUserResponse(User user) {
        UserRole userRole = findUserRole(user).orElse(null);

        return userManagementMapper.toUserResponse(user, userRole);
    }

    private Optional<UUID> parseTenantId() {
        
        if (!tenantContext.hasTenant()) {
            return Optional.empty();
        }
        return Optional.of(tenantContext.getTenantId());
    }

    private boolean isTenantAdmin(UserRole currentUserRole) {
        return currentUserRole != null
                && currentUserRole.getTenantId() != null
                && "ADMIN".equalsIgnoreCase(currentUserRole.getRole().getName());
    }

    private boolean isSuperAdmin(UserRole currentUserRole) {
        return currentUserRole != null
                && currentUserRole.getTenantId() == null
                && "SUPERADMIN".equalsIgnoreCase(currentUserRole.getRole().getName());
    }

    private void createTenantUser(CreateUserRequest request, Role targetRole, UUID tenantId) {
        if (userRepository.existsByTenantIdAndEmail(tenantId, request.getEmail())) {
            throw new BusinessException("EMAIL_EXISTS", "User with this email already exists in the tenant");
        }

        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        user.setEmailVerified(false);
        user.setRoleId(targetRole.getId());
        userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(targetRole.getId());
        userRole.setTenantId(tenantId);
        userRoleRepository.save(userRole);
    }

    private void createPlatformUser(CreateUserRequest request, Role targetRole) {
        if (!isPlatformRole(targetRole)) {
            throw new BusinessException("FORBIDDEN", "Platform users may only be created with platform roles");
        }

        if (userRepository.existsByTenantIdIsNullAndEmail(request.getEmail())) {
            throw new BusinessException("EMAIL_EXISTS", "Platform user with this email already exists");
        }

        User user = new User();
        user.setTenantId(null);
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        user.setEmailVerified(false);
        user.setRoleId(targetRole.getId());
        userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(targetRole.getId());
        userRole.setTenantId(null);
        userRoleRepository.save(userRole);
    }

    private void createTenantScopedUserAsSuperAdmin(CreateUserRequest request, Role targetRole) {
        UUID tenantId = targetRole.getTenantId();
        if (tenantId == null || isPlatformRole(targetRole)) {
            throw new BusinessException("INVALID_ROLE", "Target role must be tenant-scoped for tenant user creation");
        }

        if (request.getTenantId() != null && !request.getTenantId().equals(tenantId)) {
            throw new BusinessException("INVALID_REQUEST", "Tenant ID does not match the selected role");
        }

        if (userRepository.existsByTenantIdAndEmail(tenantId, request.getEmail())) {
            throw new BusinessException("EMAIL_EXISTS", "User with this email already exists in the tenant");
        }

        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        user.setEmailVerified(false);
        user.setRoleId(targetRole.getId());
        userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(targetRole.getId());
        userRole.setTenantId(tenantId);
        userRoleRepository.save(userRole);
    }

    private User findCreatedUser(String email, UUID tenantId) {
        if (tenantId == null) {
            return userRepository.findPlatformUserByEmail(email.toLowerCase().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("User", email));
        }

        return userRepository.findByTenantIdAndEmail(tenantId, email.toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    private Optional<UserRole> findUserRole(User user) {
        if (user.getTenantId() == null) {
            return userRoleRepository.findPlatformRole(user.getId());
        }

        return userRoleRepository.findByUserIdAndTenantId(user.getId(), user.getTenantId());
    }

    private boolean isPlatformRole(Role role) {
        return role.getTenantId() == null || "PLATFORM".equalsIgnoreCase(role.getLevel());
    }

    public boolean isActive(String userId) {
        Optional<User> user = userRepository.findById(UUID.fromString(userId));
        return user.isPresent() && user.get().getIsActive();
    }
}
