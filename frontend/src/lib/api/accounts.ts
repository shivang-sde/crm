import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import {
  AccountCreateRequest,
  AccountListMeta,
  AccountListParams,
  AccountResponse,
  AccountUpdateRequest,
} from "@/types/accounts";
import { ContactResponse } from "@/types/contacts";
import { EntityActivityResponse, EntityNoteResponse } from "@/types/entities";

import { unwrapResponse, unwrapListResponse } from "./api-utils";

export const accountApi = {
  listAccounts: async (params: AccountListParams = {}) => {
    const response = await api.get<ApiResponse<AccountResponse[]>>("/accounts", { params });
    const { data, meta } = unwrapListResponse<AccountResponse, Partial<AccountListMeta>>(response);
    return {
      data,
      meta: {
        page: meta.page ?? 0,
        size: meta.size ?? 20,
        total: meta.total ?? data.length,
        totalPages: meta.totalPages ?? 1,
      },
    };
  },

  getAccount: async (id: string) => {
    const response = await api.get<ApiResponse<AccountResponse>>(`/accounts/${id}`);
    return unwrapResponse(response);
  },

  createAccount: async (payload: AccountCreateRequest) => {
    const response = await api.post<ApiResponse<AccountResponse>>("/accounts", payload);
    return unwrapResponse(response);
  },

  updateAccount: async (id: string, payload: AccountUpdateRequest) => {
    const response = await api.put<ApiResponse<AccountResponse>>(`/accounts/${id}`, payload);
    return unwrapResponse(response);
  },

  deleteAccount: async (id: string) => {
    await api.delete(`/accounts/${id}`);
  },

  getAccountContacts: async (accountId: string, params: { page?: number; size?: number } = {}) => {
    const response = await api.get<ApiResponse<ContactResponse[]>>(
      `/accounts/${accountId}/contacts`,
      { params }
    );
    const { data, meta } = unwrapListResponse<ContactResponse, Partial<AccountListMeta>>(response);
    return {
      data,
      meta: {
        page: meta.page ?? 0,
        size: meta.size ?? 20,
        total: meta.total ?? data.length,
        totalPages: meta.totalPages ?? 1,
      },
    };
  },

  searchAccounts: async (query: string) => {
    const response = await api.get<ApiResponse<AccountResponse[]>>("/accounts/search", {
      params: { q: query },
    });
    return unwrapResponse(response);
  },

  getActivities: async (accountId: string, page = 0, size = 50) => {
    const response = await api.get<ApiResponse<EntityActivityResponse[]>>(
      `/accounts/${accountId}/activities`,
      { params: { page, size } }
    );
    const { data, meta } = unwrapListResponse<EntityActivityResponse, Partial<AccountListMeta>>(response);
    return {
      data,
      meta: {
        page: meta.page ?? 0,
        size: meta.size ?? 20,
        total: meta.total ?? data.length,
        totalPages: meta.totalPages ?? 1,
      },
    };
  },

  getNotes: async (accountId: string, page = 0, size = 50) => {
    const response = await api.get<ApiResponse<EntityNoteResponse[]>>(
      `/accounts/${accountId}/notes`,
      { params: { page, size } }
    );
    const { data, meta } = unwrapListResponse<EntityNoteResponse, Partial<AccountListMeta>>(response);
    return {
      data,
      meta: {
        page: meta.page ?? 0,
        size: meta.size ?? 20,
        total: meta.total ?? data.length,
        totalPages: meta.totalPages ?? 1,
      },
    };
  },

  addNote: async (accountId: string, note: string) => {
    const response = await api.post<ApiResponse<EntityNoteResponse>>(
      `/accounts/${accountId}/notes`,
      { note }
    );
    return unwrapResponse(response);
  },

  deleteNote: async (accountId: string, noteId: string) => {
    await api.delete(`/accounts/${accountId}/notes/${noteId}`);
  },
};
