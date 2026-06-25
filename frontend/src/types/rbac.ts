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

export interface RolePermission {
  id: string;
  module: string;
  action: string;
  accessScope: 'ALL' | 'TEAM' | 'OWN' | 'NONE';
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

export interface CreateRoleRequest {
  name: string;
  description?: string;
  parentRoleId?: string;
}

export interface UpdateRoleRequest {
  name?: string;
  description?: string;
  parentRoleId?: string;
}

export interface AssignPermissionRequest {
  permissionId: string;
  accessScope: 'ALL' | 'TEAM' | 'OWN' | 'NONE';
}
