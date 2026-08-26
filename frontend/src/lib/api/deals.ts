import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import {
  DealResponse,
  DealCreateRequest,
  DealUpdateRequest,
  DealListParams,
  DealListMeta,
  DealActivityResponse,
  DealNoteResponse,
} from "@/types/deals";
import { SalesDashboardResponse } from "@/types/sales-dashboard";
import { unwrapResponse, unwrapListResponse } from "./api-utils";

export const dealApi = {
  getDashboard: async () => {
    const response = await api.get<ApiResponse<SalesDashboardResponse>>("/deals/dashboard");
    return unwrapResponse(response);
  },

  listDeals: async (params: DealListParams = {}) => {
    const response = await api.get<ApiResponse<DealResponse[]>>("/deals", { params });
    return unwrapListResponse<DealResponse, DealListMeta>(response);
  },

  getDeal: async (id: string) => {
    const response = await api.get<ApiResponse<DealResponse>>(`/deals/${id}`);
    return unwrapResponse(response);
  },

  createDeal: async (data: DealCreateRequest) => {
    const response = await api.post<ApiResponse<DealResponse>>("/deals", data);
    return unwrapResponse(response);
  },

  updateDeal: async (id: string, data: DealUpdateRequest) => {
    const response = await api.put<ApiResponse<DealResponse>>(`/deals/${id}`, data);
    return unwrapResponse(response);
  },

  deleteDeal: async (id: string): Promise<void> => {
    await api.delete(`/deals/${id}`);
  },

  changeStage: async (id: string, stageId: string, reasons?: { wonReason?: string; lostReason?: string }) => {
    if (reasons?.wonReason || reasons?.lostReason) {
      return dealApi.updateDeal(id, { stageId, ...reasons });
    }
    const response = await api.patch<ApiResponse<DealResponse>>(`/deals/${id}/stage`, { stageId });
    return unwrapResponse(response);
  },

  assignDeal: async (id: string, ownerUserId: string) => {
    const response = await api.put<ApiResponse<DealResponse>>(`/deals/${id}/assign`, { ownerUserId });
    return unwrapResponse(response);
  },

  markWon: async (id: string, wonReason?: string, stageId?: string) => {
    if (wonReason && stageId) {
      return dealApi.updateDeal(id, { stageId, wonReason });
    }
    const response = await api.patch<ApiResponse<DealResponse>>(`/deals/${id}/won`);
    return unwrapResponse(response);
  },

  markLost: async (id: string, lostReason?: string, stageId?: string) => {
    if (lostReason && stageId) {
      return dealApi.updateDeal(id, { stageId, lostReason });
    }
    const response = await api.patch<ApiResponse<DealResponse>>(`/deals/${id}/lost`);
    return unwrapResponse(response);
  },

  getActivities: async (dealId: string, page = 0, size = 50) => {
    const response = await api.get<ApiResponse<DealActivityResponse[]>>(`/deals/${dealId}/activities`, { params: { page, size } });
    return unwrapListResponse<DealActivityResponse>(response);
  },

  getNotes: async (dealId: string, page = 0, size = 50) => {
    const response = await api.get<ApiResponse<DealNoteResponse[]>>(`/deals/${dealId}/notes`, { params: { page, size } });
    return unwrapListResponse<DealNoteResponse>(response);
  },

  addNote: async (dealId: string, note: string) => {
    const response = await api.post<ApiResponse<any>>(`/deals/${dealId}/notes`, { note });
    return unwrapResponse(response);
  },

  deleteNote: async (dealId: string, noteId: string): Promise<void> => {
    await api.delete(`/deals/${dealId}/notes/${noteId}`);
  },
};
