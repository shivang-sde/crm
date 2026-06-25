package com.shivang.crm.modules.rbac.service;

import java.util.List;
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
import com.shivang.crm.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RoleManagementService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleMapper roleMapper;
    private final TenantContext tenantContext;


    public RoleResponse getRole(UUID roleId) {

        String tenantIdStr = tenantContext.getTenantId();
        UUID tenantId = null;


        // Only parse if tenantIdStr is not null and is a valid UUID
        if (tenantIdStr != null && !tenantIdStr.isEmpty()) {
            try {
                tenantId = UUID.fromString(tenantIdStr);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid tenant ID format: {}", tenantIdStr);
                tenantId = null;
            }
        }
        
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

        role.setName(request.getName().toUpperCase());
        role.setDescription(request.getDescription());
        role = roleRepository.save(role);

        // Clear existing permissions and save new ones
        rolePermissionRepository.deleteAll(rolePermissionRepository.findByRoleId(roleId));
        saveRolePermissions(roleId, request.getPermissions());

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

        RolePermission rolePerm = new RolePermission();
        rolePerm.setRoleId(roleId);
        rolePerm.setPermissionId(permission.getId());
        rolePerm.setAccessScope(request.getAccessScope());
        rolePermissionRepository.save(rolePerm);
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

        rolePerm.setAccessScope(scope.toUpperCase());
        rolePermissionRepository.save(rolePerm);
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
        String tenantId = tenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }

        return UUID.fromString(tenantId);
    }
}
