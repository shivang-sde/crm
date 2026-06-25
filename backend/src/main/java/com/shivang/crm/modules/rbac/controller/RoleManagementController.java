package com.shivang.crm.modules.rbac.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.rbac.dto.request.CreateRoleRequest;
import com.shivang.crm.modules.rbac.dto.request.PermissionScopeRequest;
import com.shivang.crm.modules.rbac.dto.request.UpdateRoleRequest;
import com.shivang.crm.modules.rbac.service.RoleManagementService;
import com.shivang.crm.modules.user.dto.response.PermissionResponse;
import com.shivang.crm.modules.user.dto.response.RoleResponse;
import com.shivang.crm.shared.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleManagementController {

    private final RoleManagementService roleManagementService;

    /** GET /roles — list all roles in current tenant/platform context */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getRoles() {
        return ResponseEntity.ok(ApiResponse.success(roleManagementService.getRoles()));
    }

    /** GET /roles/permissions — list all available permissions (catalog) */
    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        return ResponseEntity.ok(ApiResponse.success(roleManagementService.getPermissions()));
    }

    /** POST /roles — create a new role */
    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roleManagementService.createRole(request)));
    }

    /** GET /roles/{roleId} — get role details */
    @GetMapping("/{roleId}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRole(@PathVariable UUID roleId) {
        return ResponseEntity.ok(ApiResponse.success(roleManagementService.getRole(roleId)));
    }

    /**
     * GET /roles/{roleId}/permissions — list permissions assigned to a specific
     * role
     */
    @GetMapping("/{roleId}/permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getRolePermissions(@PathVariable UUID roleId) {
        return ResponseEntity.ok(ApiResponse.success(roleManagementService.getRolePermissions(roleId)));
    }

    /** POST /roles/{roleId}/permissions — assign a permission to a role */
    @PostMapping("/{roleId}/permissions")
    public ResponseEntity<ApiResponse<Void>> assignPermission(
            @PathVariable UUID roleId,
            @Valid @RequestBody PermissionScopeRequest request) {
        roleManagementService.assignPermission(roleId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * DELETE /roles/{roleId}/permissions/{permissionId} — remove a permission from
     * a role
     */
    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    public ResponseEntity<ApiResponse<Void>> removePermission(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId) {
        roleManagementService.removePermission(roleId, permissionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * PUT /roles/{roleId}/permissions/{permissionId}/scope?scope=ALL — update
     * access scope
     */
    @PutMapping("/{roleId}/permissions/{permissionId}/scope")
    public ResponseEntity<ApiResponse<Void>> updatePermissionScope(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId,
            @RequestParam String scope) {
        roleManagementService.updatePermissionScope(roleId, permissionId, scope);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** PUT /roles/{roleId} — update role metadata + permissions bulk */
    @PutMapping("/{roleId}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roleManagementService.updateRole(roleId, request)));
    }

    /** DELETE /roles/{roleId} — delete a role */
    @DeleteMapping("/{roleId}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable UUID roleId) {
        roleManagementService.deleteRole(roleId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
