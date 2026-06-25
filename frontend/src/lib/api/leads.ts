import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import {
  LeadActivityResponse,
  LeadConvertRequest,
  LeadConvertResponse,
  LeadCreateRequest,
  LeadCustomFieldCreateRequest,
  LeadCustomFieldResponse,
  LeadListMeta,
  LeadListParams,
  LeadNoteResponse,
  LeadResponse,
  LeadSourceCreateRequest,
  LeadSourceSummary,
  LeadStatusCreateRequest,
  LeadStatusSummary,
  LeadUpdateRequest,
} from "@/types/leads";

import { unwrapResponse, unwrapListResponse } from "./api-utils";

export const leadApi = {
  listLeads: async (params: LeadListParams = {}) => {
    const response = await api.get<ApiResponse<LeadResponse[]>>("/leads", { params });
    const { data, meta } = unwrapListResponse<LeadResponse, Partial<LeadListMeta>>(response);
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

  getLead: async (id: string) => {
    const response = await api.get<ApiResponse<LeadResponse>>(`/leads/${id}`);
    return unwrapResponse(response);
  },

  createLead: async (data: LeadCreateRequest) => {
    const response = await api.post<ApiResponse<LeadResponse>>("/leads", data);
    return unwrapResponse(response);
  },

  updateLead: async (id: string, data: LeadUpdateRequest) => {
    const response = await api.put<ApiResponse<LeadResponse>>(`/leads/${id}`, data);
    return unwrapResponse(response);
  },

  deleteLead: async (id: string) => {
    await api.delete(`/leads/${id}`);
  },

  assignLead: async (id: string, ownerUserId: string) => {
    const response = await api.put<ApiResponse<LeadResponse>>(`/leads/${id}/assign`, {
      ownerUserId,
    });
    return unwrapResponse(response);
  },

  changeLeadStatus: async (id: string, statusId: string) => {
    const response = await api.put<ApiResponse<LeadResponse>>(`/leads/${id}/status`, {
      statusId,
    });
    return unwrapResponse(response);
  },

  getActivities: async (leadId: string, page = 0, size = 50) => {
    const response = await api.get<ApiResponse<LeadActivityResponse[]>>(
      `/leads/${leadId}/activities`,
      { params: { page, size } }
    );
    const { data, meta } = unwrapListResponse<LeadActivityResponse, Partial<LeadListMeta>>(response);
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

  getNotes: async (leadId: string, page = 0, size = 50) => {
    const response = await api.get<ApiResponse<LeadNoteResponse[]>>(
      `/leads/${leadId}/notes`,
      { params: { page, size } }
    );
    const { data, meta } = unwrapListResponse<LeadNoteResponse, Partial<LeadListMeta>>(response);
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

  addNote: async (leadId: string, note: string) => {
    const response = await api.post<ApiResponse<LeadNoteResponse>>(
      `/leads/${leadId}/notes`,
      { note }
    );
    return unwrapResponse(response);
  },

  deleteNote: async (leadId: string, noteId: string) => {
    await api.delete(`/leads/${leadId}/notes/${noteId}`);
  },

  convertLead: async (id: string, payload: LeadConvertRequest) => {
    const response = await api.post<ApiResponse<LeadConvertResponse>>(
      `/leads/${id}/convert`,
      payload
    );
    return unwrapResponse(response);
  },

  listStatuses: async () => {
    const response = await api.get<ApiResponse<LeadStatusSummary[]>>("/lead-statuses");
    return unwrapResponse(response);
  },

  listSources: async () => {
    const response = await api.get<ApiResponse<LeadSourceSummary[]>>("/lead-sources");
    return unwrapResponse(response);
  },

  listCustomFields: async () => {
    const response = await api.get<ApiResponse<LeadCustomFieldResponse[]>>(
      "/lead-custom-fields"
    );
    return unwrapResponse(response);
  },

  createStatus: async (data: LeadStatusCreateRequest) => {
    const response = await api.post<ApiResponse<LeadStatusSummary>>(
      "/lead-statuses",
      data
    );
    return unwrapResponse(response);
  },

  updateStatus: async (id: string, data: LeadStatusCreateRequest) => {
    const response = await api.put<ApiResponse<LeadStatusSummary>>(
      `/lead-statuses/${id}`,
      data
    );
    return unwrapResponse(response);
  },

  deleteStatus: async (id: string) => {
    await api.delete(`/lead-statuses/${id}`);
  },

  createSource: async (data: LeadSourceCreateRequest) => {
    const response = await api.post<ApiResponse<LeadSourceSummary>>(
      "/lead-sources",
      data
    );
    return unwrapResponse(response);
  },

  updateSource: async (id: string, data: LeadSourceCreateRequest) => {
    const response = await api.put<ApiResponse<LeadSourceSummary>>(
      `/lead-sources/${id}`,
      data
    );
    return unwrapResponse(response);
  },

  deleteSource: async (id: string) => {
    await api.delete(`/lead-sources/${id}`);
  },

  createCustomField: async (data: LeadCustomFieldCreateRequest) => {
    const response = await api.post<ApiResponse<LeadCustomFieldResponse>>(
      "/lead-custom-fields",
      data
    );
    return unwrapResponse(response);
  },

  updateCustomField: async (id: string, data: LeadCustomFieldCreateRequest) => {
    const response = await api.put<ApiResponse<LeadCustomFieldResponse>>(
      `/lead-custom-fields/${id}`,
      data
    );
    return unwrapResponse(response);
  },

  deleteCustomField: async (id: string) => {
    await api.delete(`/lead-custom-fields/${id}`);
  },
};
