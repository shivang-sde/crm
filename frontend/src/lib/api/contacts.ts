import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import {
  ContactCreateRequest,
  ContactListMeta,
  ContactListParams,
  ContactResponse,
  ContactUpdateRequest,
} from "@/types/contacts";
import { EntityActivityResponse, EntityNoteResponse } from "@/types/entities";

import { unwrapResponse, unwrapListResponse } from "./api-utils";

export const contactApi = {
  listContacts: async (params: ContactListParams = {}) => {
    const response = await api.get<ApiResponse<ContactResponse[]>>("/contacts", { params });
    const { data, meta } = unwrapListResponse<ContactResponse, Partial<ContactListMeta>>(response);
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

  getContact: async (id: string) => {
    const response = await api.get<ApiResponse<ContactResponse>>(`/contacts/${id}`);
    return unwrapResponse(response);
  },

  createContact: async (payload: ContactCreateRequest) => {
    const response = await api.post<ApiResponse<ContactResponse>>("/contacts", payload);
    return unwrapResponse(response);
  },

  updateContact: async (id: string, payload: ContactUpdateRequest) => {
    const response = await api.put<ApiResponse<ContactResponse>>(`/contacts/${id}`, payload);
    return unwrapResponse(response);
  },

  deleteContact: async (id: string) => {
    await api.delete(`/contacts/${id}`);
  },

  searchContacts: async (query: string) => {
    const response = await api.get<ApiResponse<ContactResponse[]>>("/contacts/search", {
      params: { q: query },
    });
    return unwrapResponse(response);
  },

  getActivities: async (contactId: string, page = 0, size = 50) => {
    const response = await api.get<ApiResponse<EntityActivityResponse[]>>(
      `/contacts/${contactId}/activities`,
      { params: { page, size } }
    );
    const { data, meta } = unwrapListResponse<EntityActivityResponse, Partial<ContactListMeta>>(response);
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

  getNotes: async (contactId: string, page = 0, size = 50) => {
    const response = await api.get<ApiResponse<EntityNoteResponse[]>>(
      `/contacts/${contactId}/notes`,
      { params: { page, size } }
    );
    const { data, meta } = unwrapListResponse<EntityNoteResponse, Partial<ContactListMeta>>(response);
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

  addNote: async (contactId: string, note: string) => {
    const response = await api.post<ApiResponse<EntityNoteResponse>>(
      `/contacts/${contactId}/notes`,
      { note }
    );
    return unwrapResponse(response);
  },

  deleteNote: async (contactId: string, noteId: string) => {
    await api.delete(`/contacts/${contactId}/notes/${noteId}`);
  },
};
