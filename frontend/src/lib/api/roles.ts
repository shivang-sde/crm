import { api } from "./client";
import {
  CreateRoleRequest,
  UpdateRoleRequest,
  Role,
  Permission,
  AssignPermissionRequest,
  RolePermission,
} from "@/types/rbac";
import { ApiResponse } from "@/types/auth";
import { unwrapResponse } from "./api-utils";

export const roleApi = {
  getRoles: async () => {
    const response = await api.get<ApiResponse<Role[]>>("/roles");
    return unwrapResponse(response);
  },

  getRole: async (roleId: string) => {
    const response = await api.get<ApiResponse<Role>>(`/roles/${roleId}`);
    return unwrapResponse(response);
  },

  createRole: async (data: CreateRoleRequest) => {
    const response = await api.post<ApiResponse<Role>>("/roles", data);
    return unwrapResponse(response);
  },

  updateRole: async (roleId: string, data: UpdateRoleRequest) => {
    const response = await api.put<ApiResponse<Role>>(`/roles/${roleId}`, data);
    return unwrapResponse(response);
  },

  deleteRole: async (roleId: string) => {
    await api.delete(`/roles/${roleId}`);
  },

  getAllPermissions: async () => {
    const response = await api.get<ApiResponse<Permission[]>>("/roles/permissions"); // Note: backend URL in prompt was /roles/permissions
    return unwrapResponse(response);
  },

  getRolePermissions: async (roleId: string) => {
    const response = await api.get<ApiResponse<RolePermission[]>>(`/roles/${roleId}/permissions`);
    return unwrapResponse(response);
  },

  assignPermission: async (roleId: string, data: AssignPermissionRequest) => {
    const response = await api.post<ApiResponse<void>>(`/roles/${roleId}/permissions`, data);
    return unwrapResponse(response);
  },

  removePermission: async (roleId: string, permissionId: string) => {
    const response = await api.delete<ApiResponse<void>>(`/roles/${roleId}/permissions/${permissionId}`);
    return unwrapResponse(response);
  },

  updatePermissionScope: async (roleId: string, permissionId: string, scope: string) => {
    const response = await api.put<ApiResponse<void>>(`/roles/${roleId}/permissions/${permissionId}/scope?scope=${scope}`);
    return unwrapResponse(response);
  },
};
