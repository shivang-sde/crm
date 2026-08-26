export interface Role {
  id: string;
  name: string;
  level: 'PLATFORM' | 'TENANT';
  description: string | null;
  parentRoleId: string | null;
  parentRoleName: string | null;
  isDefault: boolean;
  userCount: number;
  permissions?: RolePermission[];
  createdAt: string;
  updatedAt: string;
}


export interface Permission {
  id: string;
  module: string;
  action: string;
  description: string;
}

export type AccessScope = 'ALL' | 'TEAM' | 'OWN' | 'NONE';

export interface RolePermission {
  id: string;
  module: string;
  action: string;
  accessScope: AccessScope;
  description: string;

}

export interface RolePermissions {
  id: string;
  name: string;
  description: string;
  level: 'PLATFORM' | 'TENANT';
  permissions: Permission[];
}

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  isActive: boolean;
  emailVerified: boolean;
  roleId: string | null;
  roleName: string | null;
  managerId?: string | null;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface CreateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  password: string;
  roleId: string;
  managerId?: string | null;
  isActive?: boolean;
  tenantId?: string;
}

export interface UpdateUserRequest {
  firstName?: string;
  lastName?: string;
  isActive?: boolean;
  roleId?: string;
  managerId?: string | null;
}

// Mirrors backend PermissionScopeRequest: the only permission payload shape
// create/update accept. The full explicit set is submitted on every save.
export interface PermissionScopeRequest {
  permissionId: string;
  accessScope: AccessScope;
}

export interface CreateRoleRequest {
  name: string;
  description?: string;
  permissions?: PermissionScopeRequest[];
}

export interface UpdateRoleRequest {
  name: string;
  description?: string;
  permissions: PermissionScopeRequest[];
}

export interface AssignPermissionRequest {
  permissionId: string;
  accessScope: AccessScope;
}
