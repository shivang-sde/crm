import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import {
  OfferingCreateRequest,
  OfferingListMeta,
  OfferingListParams,
  OfferingResponse,
  OfferingUpdateRequest,
} from "@/types/offerings";
import { unwrapResponse, unwrapListResponse } from "./api-utils";

export const offeringApi = {
  listOfferings: async (params: OfferingListParams = {}) => {
    const response = await api.get<ApiResponse<OfferingResponse[]>>("/offerings", { params });
    return unwrapListResponse<OfferingResponse, OfferingListMeta>(response);
  },

  getOffering: async (id: string) => {
    const response = await api.get<ApiResponse<OfferingResponse>>(`/offerings/${id}`);
    return unwrapResponse(response);
  },

  createOffering: async (data: OfferingCreateRequest) => {
    const response = await api.post<ApiResponse<OfferingResponse>>("/offerings", {
      ...data,
      code: data.code?.toUpperCase(),
    });
    return unwrapResponse(response);
  },

  updateOffering: async (id: string, data: OfferingUpdateRequest) => {
    const response = await api.put<ApiResponse<OfferingResponse>>(`/offerings/${id}`, {
      ...data,
      code: data.code?.toUpperCase(),
    });
    return unwrapResponse(response);
  },

  activateOffering: async (id: string) => {
    const response = await api.patch<ApiResponse<string>>(`/offerings/${id}/activate`);
    return unwrapResponse(response);
  },

  deactivateOffering: async (id: string) => {
    const response = await api.patch<ApiResponse<string>>(`/offerings/${id}/deactivate`);
    return unwrapResponse(response);
  },

  deleteOffering: async (id: string): Promise<void> => {
    await api.delete(`/offerings/${id}`);
  },
};
