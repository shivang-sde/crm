package com.shivang.crm.modules.rbac.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.rbac.dto.request.CreateRoleRequest;
import com.shivang.crm.modules.rbac.dto.request.PermissionScopeRequest;
import com.shivang.crm.modules.rbac.dto.request.UpdateRoleRequest;
import com.shivang.crm.modules.rbac.entity.Permission;
import com.shivang.crm.modules.rbac.entity.Role;
import com.shivang.crm.modules.rbac.entity.RolePermission;
import com.shivang.crm.modules.rbac.entity.UserRole;
import com.shivang.crm.modules.rbac.mapper.RoleMapper;
import com.shivang.crm.modules.rbac.repository.PermissionRepository;
import com.shivang.crm.modules.rbac.repository.RolePermissionRepository;
import com.shivang.crm.modules.rbac.repository.RoleRepository;
import com.shivang.crm.modules.rbac.repository.UserRoleRepository;
import com.shivang.crm.modules.user.dto.response.PermissionResponse;
import com.shivang.crm.modules.user.dto.response.RoleResponse;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.PermissionDeniedException;
import com.shivang.crm.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RoleManagementService {

    /*
     * Scope delegation ranks (RBAC-6). An actor may only delegate a scope
     * whose rank does not exceed the rank of the scope they themselves hold
     * for the same catalog permission. NONE/missing/unrecognized scopes rank
     * zero and can never be delegated.
     */
    private static final int SCOPE_RANK_NONE = 0;
    private static final int SCOPE_RANK_OWN = 1;
    private static final int SCOPE_RANK_TEAM = 2;
    private static final int SCOPE_RANK_ALL = 3;

    private static final String DELEGATION_DENIED = "DELEGATION_DENIED";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleMapper roleMapper;
    private final TenantContext tenantContext;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final PermissionCacheEvictor permissionCacheEvictor;


    public RoleResponse getRole(UUID roleId) {
        UUID tenantId = tenantContext.getTenantId();

           Role role = roleRepository.findById(roleId)
        .orElseThrow(() -> new ResourceNotFoundException("Role", roleId.toString()));
    
    // Permission check:
    // 1. If role has no tenant (platform role like SUPERADMIN) - only accessible by platform users
    // 2. If role has tenant - must match current user's tenant
    if (role.getTenantId() == null) {
        // Platform role - only allow access if current user is platform user (tenantId is null)
        if (tenantId != null) {
            throw new BusinessException("FORBIDDEN", "Cannot access platform role from tenant context");
        }
    } else {
        // Tenant role - must match current user's tenant
        if (tenantId == null || !role.getTenantId().equals(tenantId)) {
            throw new BusinessException("FORBIDDEN", "Cannot access cross-tenant roles");
        }
    }

    List<RolePermission> permissions = rolePermissionRepository.findByRoleId(role.getId());
    return roleMapper.toRoleResponse(role, permissions);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getRoles() {
        UUID tenantId = parseTenantId();
        List<Role> roles = tenantId == null
            ? roleRepository.findByLevel("PLATFORM")
            : roleRepository.findByTenantId(tenantId);
        
        return roles.stream().map(role -> {
            List<RolePermission> permissions = rolePermissionRepository.findByRoleId(role.getId());
            return roleMapper.toRoleResponse(role, permissions);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getPermissions() {
        return permissionRepository.findAll().stream().map(p -> 
            PermissionResponse.builder()
                .id(p.getId())
                .module(p.getModule())
                .action(p.getAction())
                .accessScope("ALL") // Default or dummy scope for UI listing
                .build()
        ).collect(Collectors.toList());
    }

    public RoleResponse createRole(CreateRoleRequest request) {
        UUID tenantId = parseTenantId();
        
        if (roleRepository.findByNameAndTenantId(request.getName().toUpperCase(), tenantId).isPresent()) {
            throw new BusinessException("ROLE_EXISTS", "A role with this name already exists");
        }

        // RBAC-6: delegation boundary on the complete requested set.
        validateDelegationForCreate(request.getPermissions());

        Role role = new Role();
        role.setName(request.getName().toUpperCase());
        role.setDescription(request.getDescription());
        role.setLevel(tenantId == null ? "PLATFORM" : "TENANT");
        role.setTenantId(tenantId);
        role = roleRepository.save(role);

        saveRolePermissions(role.getId(), request.getPermissions());

        List<RolePermission> permissions = rolePermissionRepository.findByRoleId(role.getId());
        return roleMapper.toRoleResponse(role, permissions);
    }

    public RoleResponse updateRole(UUID roleId, UpdateRoleRequest request) {
        UUID tenantId = parseTenantId();
        
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Role", roleId.toString()));
            
        if (tenantId == null) {
            if (role.getTenantId() != null) {
                throw new BusinessException("FORBIDDEN", "Cannot modify tenant roles from platform context");
            }
        } else if (role.getTenantId() == null || !role.getTenantId().equals(tenantId)) {
            throw new BusinessException("FORBIDDEN", "Cannot modify platform or cross-tenant roles");
        }

        if (List.of("SUPERADMIN", "RESELLER", "ADMIN", "MANAGER", "EMPLOYEE").contains(role.getName())) {
            throw new BusinessException("FORBIDDEN", "Cannot modify default system roles");
        }

        // RBAC-6: delegation boundary on the resulting permission set
        // (additions and scope escalations only). Validated before mutation.
        validateDelegationForUpdate(roleId, request.getPermissions());

        role.setName(request.getName().toUpperCase());
        role.setDescription(request.getDescription());
        role = roleRepository.save(role);

        // Clear existing permissions and save new ones
        rolePermissionRepository.deleteAll(rolePermissionRepository.findByRoleId(roleId));
        saveRolePermissions(roleId, request.getPermissions());

        // RBAC-8: role permissions changed -> evict every assignee's cache.
        permissionCacheEvictor.evictRoleUsersAfterCommit(roleId);

        List<RolePermission> permissions = rolePermissionRepository.findByRoleId(roleId);
        return roleMapper.toRoleResponse(role, permissions);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getRolePermissions(UUID roleId) {
        UUID tenantId = parseTenantId();
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Role", roleId.toString()));

        if (role.getTenantId() == null) {
            if (tenantId != null) {
                throw new BusinessException("FORBIDDEN", "Cannot access platform role from tenant context");
            }
        } else {
            if (tenantId == null || !role.getTenantId().equals(tenantId)) {
                throw new BusinessException("FORBIDDEN", "Cannot access cross-tenant roles");
            }
        }

        return rolePermissionRepository.findByRoleId(roleId).stream()
            .map(roleMapper::toPermissionResponse)
            .collect(Collectors.toList());
    }

    public void assignPermission(UUID roleId, PermissionScopeRequest request) {
        UUID tenantId = parseTenantId();
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Role", roleId.toString()));

        if (tenantId != null && (role.getTenantId() == null || !role.getTenantId().equals(tenantId))) {
            throw new BusinessException("FORBIDDEN", "Cannot modify platform or cross-tenant roles");
        }

        Permission permission = permissionRepository.findById(request.getPermissionId())
            .orElseThrow(() -> new ResourceNotFoundException("Permission", request.getPermissionId().toString()));

        if (rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permission.getId()).isPresent()) {
            throw new BusinessException("ALREADY_ASSIGNED", "Permission is already assigned to this role");
        }

        // RBAC-6: adding a permission to a target role is always a grant.
        UUID actorId = requireActorId();
        if (!permissionEvaluatorService.isSuperadmin(actorId)) {
            assertCanDelegate(actorId, tenantId, permission.getModule(), permission.getAction(), request.getAccessScope());
        }

        RolePermission rolePerm = new RolePermission();
        rolePerm.setRoleId(roleId);
        rolePerm.setPermissionId(permission.getId());
        rolePerm.setAccessScope(request.getAccessScope());
        rolePermissionRepository.save(rolePerm);

        // RBAC-8
        permissionCacheEvictor.evictRoleUsersAfterCommit(roleId);
    }

    public void removePermission(UUID roleId, UUID permissionId) {
        UUID tenantId = parseTenantId();
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Role", roleId.toString()));

        if (tenantId != null && (role.getTenantId() == null || !role.getTenantId().equals(tenantId))) {
            throw new BusinessException("FORBIDDEN", "Cannot modify platform or cross-tenant roles");
        }

        RolePermission rolePerm = rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId)
            .orElseThrow(() -> new ResourceNotFoundException("RolePermission", permissionId.toString()));

        rolePermissionRepository.delete(rolePerm);

        // RBAC-8
        permissionCacheEvictor.evictRoleUsersAfterCommit(roleId);
    }

    public void updatePermissionScope(UUID roleId, UUID permissionId, String scope) {
        UUID tenantId = parseTenantId();
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Role", roleId.toString()));

        if (tenantId != null && (role.getTenantId() == null || !role.getTenantId().equals(tenantId))) {
            throw new BusinessException("FORBIDDEN", "Cannot modify platform or cross-tenant roles");
        }

        List<String> validScopes = List.of("ALL", "TEAM", "OWN", "NONE");
        if (!validScopes.contains(scope.toUpperCase())) {
            throw new BusinessException("INVALID_SCOPE", "Access scope must be one of: ALL, TEAM, OWN, NONE");
        }

        RolePermission rolePerm = rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId)
            .orElseThrow(() -> new ResourceNotFoundException("RolePermission", permissionId.toString()));

        // RBAC-6: scope escalation on an existing assignment is a grant.
        // Downgrades and unchanged scopes are not privilege escalations.
        if (scopeRank(scope) > scopeRank(rolePerm.getAccessScope())) {
            UUID actorId = requireActorId();
            if (!permissionEvaluatorService.isSuperadmin(actorId)) {
                Permission permission = permissionRepository.findById(permissionId)
                        .orElseThrow(() -> new ResourceNotFoundException("Permission", permissionId.toString()));
                assertCanDelegate(actorId, tenantId, permission.getModule(), permission.getAction(), scope.toUpperCase());
            }
        }

        rolePerm.setAccessScope(scope.toUpperCase());
        rolePermissionRepository.save(rolePerm);

        // RBAC-8
        permissionCacheEvictor.evictRoleUsersAfterCommit(roleId);
    }

    public void deleteRole(UUID roleId) {
        UUID tenantId = parseTenantId();

        Role roleToDelete = roleRepository.findById(roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Role", roleId.toString()));

        if (tenantId == null) {
            if (roleToDelete.getTenantId() != null) {
                throw new BusinessException("FORBIDDEN", "Cannot delete tenant roles from platform context");
            }
        } else if (roleToDelete.getTenantId() == null || !roleToDelete.getTenantId().equals(tenantId)) {
            throw new BusinessException("FORBIDDEN", "Cannot delete platform or cross-tenant roles");
        }

        if (List.of("SUPERADMIN", "RESELLER", "ADMIN", "MANAGER", "EMPLOYEE").contains(roleToDelete.getName())) {
            throw new BusinessException("FORBIDDEN", "Cannot delete default system roles");
        }

        // Ensure no users are assigned to this role
        List<UserRole> usersWithRole = userRoleRepository.findByRoleId(roleId);
        if (!usersWithRole.isEmpty()) {
            throw new BusinessException("FORBIDDEN", "Role is assigned to users; reassign or remove users before deleting");
        }

        roleRepository.delete(roleToDelete);
    }

    private void saveRolePermissions(UUID roleId, List<PermissionScopeRequest> requestedPermissions) {
        
        if (requestedPermissions == null || requestedPermissions.isEmpty()) {
        return; // Nothing to save
    }
        
        for (PermissionScopeRequest pr : requestedPermissions) {
            Permission permission = permissionRepository.findById(pr.getPermissionId())
                .orElseThrow(() -> new ResourceNotFoundException("Permission", pr.getPermissionId().toString()));
                
            RolePermission rolePerm = new RolePermission();
            rolePerm.setRoleId(roleId);
            rolePerm.setPermissionId(permission.getId());
            rolePerm.setAccessScope(pr.getAccessScope());
            rolePermissionRepository.save(rolePerm);
        }
    }

    private UUID parseTenantId() {
        return tenantContext.getTenantId();
    }

    // =====================================================================
    // RBAC-6: Role-management delegation boundary
    // =====================================================================
    // A non-SUPERADMIN actor holding admin:role_manage may only GRANT what
    // they themselves possess, at a scope they are allowed to delegate.
    // One authoritative check (assertCanDelegationAllowed) is applied to
    // every path capable of adding permissions or increasing scopes:
    // createRole, updateRole, assignPermission, updatePermissionScope.
    // Permission REMOVAL and scope DOWNGRADES are not privilege escalations
    // and remain unrestricted. SUPERADMIN keeps its centralized bypass via
    // PermissionEvaluatorService.isSuperadmin; no role-name checks exist here.
    // =====================================================================

    private int scopeRank(String scope) {
        if (scope == null) {
            return SCOPE_RANK_NONE;
        }
        return switch (scope.trim().toUpperCase()) {
            case "OWN" -> SCOPE_RANK_OWN;
            case "TEAM" -> SCOPE_RANK_TEAM;
            case "ALL" -> SCOPE_RANK_ALL;
            default -> SCOPE_RANK_NONE;
        };
    }

    private UUID requireActorId() {
        UUID actorId = tenantContext.getUserId();
        if (actorId == null) {
            throw new PermissionDeniedException(DELEGATION_DENIED,
                    "Actor identity is required to modify role permissions");
        }
        return actorId;
    }

    private void assertCanDelegate(
            UUID actorId,
            UUID tenantId,
            String module,
            String action,
            String requestedScope) {

        int requestedRank = scopeRank(requestedScope);
        if (requestedRank == SCOPE_RANK_NONE) {
            throw new PermissionDeniedException(DELEGATION_DENIED,
                    "A valid access scope (OWN, TEAM or ALL) is required");
        }

        // Centralized evaluator: undefined permission -> NONE, missing/NONE
        // actor scope -> NONE, SUPERADMIN -> ALL. Fail-closed by design.
        String actorScope = permissionEvaluatorService.getAccessScope(actorId, tenantId, module, action);

        if (scopeRank(actorScope) < requestedRank) {
            log.warn("Delegation denied: actor {} attempted to delegate {}:{} scope '{}' while holding '{}'",
                    actorId, module, action, requestedScope, actorScope);
            throw new PermissionDeniedException(DELEGATION_DENIED,
                    "You cannot grant permissions you do not hold at the requested scope");
        }
    }

    /**
     * Create path: every requested permission is new, so the complete
     * resulting set must be delegable by the actor.
     */
    private void validateDelegationForCreate(List<PermissionScopeRequest> requestedPermissions) {
        if (requestedPermissions == null || requestedPermissions.isEmpty()) {
            return;
        }

        UUID actorId = requireActorId();
        if (permissionEvaluatorService.isSuperadmin(actorId)) {
            return;
        }

        UUID tenantId = parseTenantId();
        for (PermissionScopeRequest pr : requestedPermissions) {
            Permission permission = permissionRepository.findById(pr.getPermissionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Permission", pr.getPermissionId().toString()));
            assertCanDelegate(actorId, tenantId, permission.getModule(), permission.getAction(), pr.getAccessScope());
        }
    }

    /**
     * Update path: the resulting role state is exactly the requested list
     * (existing rows are replaced). Only authority-INCREASING changes are
     * delegation events:
     *   - a permission newly added to the target role, or
     *   - an existing permission whose scope is being raised.
     * Retentions and scope downgrades are allowed without delegation rights,
     * so legitimate no-op edits never lock out non-SUPERADMIN actors.
     */
    private void validateDelegationForUpdate(UUID roleId, List<PermissionScopeRequest> requestedPermissions) {
        if (requestedPermissions == null || requestedPermissions.isEmpty()) {
            return;
        }

        UUID actorId = requireActorId();
        if (permissionEvaluatorService.isSuperadmin(actorId)) {
            return;
        }

        Map<UUID, String> existingScopes = new HashMap<>();
        for (RolePermission rp : rolePermissionRepository.findByRoleId(roleId)) {
            existingScopes.put(rp.getPermissionId(), rp.getAccessScope());
        }

        UUID tenantId = parseTenantId();
        for (PermissionScopeRequest pr : requestedPermissions) {
            Permission permission = permissionRepository.findById(pr.getPermissionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Permission", pr.getPermissionId().toString()));

            String currentScope = existingScopes.get(permission.getId());
            boolean escalation = currentScope == null
                    || scopeRank(pr.getAccessScope()) > scopeRank(currentScope);

            if (escalation) {
                assertCanDelegate(actorId, tenantId, permission.getModule(), permission.getAction(), pr.getAccessScope());
            }
        }
    }
}
