import { api } from "./client";
import {
  CreateUserRequest,
  UpdateUserRequest,
  User,
} from "@/types/rbac";
import { ApiResponse } from "@/types/auth";

export interface PaginatedResponse<T> {
  content: T[];
  pageable: unknown;
  last: boolean;
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
  sort: unknown;
  first: boolean;
  numberOfElements: number;
  empty: boolean;
}

import { unwrapResponse } from "./api-utils";

export const userApi = {
  getUsers: async (params: { page?: number; search?: string; roleId?: string; isActive?: boolean }) => {
    const response = await api.get<ApiResponse<PaginatedResponse<User>>>("/users", { params });
    return unwrapResponse(response);
  },

  getManagers: async () => {
    const response = await api.get<ApiResponse<User[]>>("/users/managers");
    return unwrapResponse(response);
  },

  getUser: async (userId: string) => {
    const response = await api.get<ApiResponse<User>>(`/users/${userId}`);
    return unwrapResponse(response);
  },

  createUser: async (data: CreateUserRequest) => {
    const response = await api.post<ApiResponse<User>>("/users", data);
    return unwrapResponse(response);
  },

  updateUser: async (userId: string, data: UpdateUserRequest) => {
    const response = await api.put<ApiResponse<User>>(`/users/${userId}`, data);
    return unwrapResponse(response);
  },

  deleteUser: async (userId: string) => {
    await api.delete(`/users/${userId}`);
  },

  activateUser: async (userId: string) => {
    await api.post(`/users/${userId}/activate`);
  },

  deactivateUser: async (userId: string) => {
    await api.post(`/users/${userId}/deactivate`);
  },
};
