import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import {
  DealLineItemCreateRequest,
  DealLineItemResponse,
  DealLineItemUpdateRequest,
} from "@/types/deal-line-items";
import { unwrapResponse, unwrapListResponse } from "./api-utils";

export const dealLineItemApi = {
  listDealLineItems: async (dealId: string) => {
    const response = await api.get<ApiResponse<DealLineItemResponse[]>>(`/deals/${dealId}/line-items`);
    return unwrapResponse(response);
  },

  createDealLineItem: async (dealId: string, data: DealLineItemCreateRequest) => {
    const response = await api.post<ApiResponse<DealLineItemResponse>>(`/deals/${dealId}/line-items`, data);
    return unwrapResponse(response);
  },

  updateDealLineItem: async (dealId: string, id: string, data: DealLineItemUpdateRequest) => {
    const response = await api.put<ApiResponse<DealLineItemResponse>>(`/deals/${dealId}/line-items/${id}`, data);
    return unwrapResponse(response);
  },

  deleteDealLineItem: async (dealId: string, id: string) => {
    await api.delete(`/deals/${dealId}/line-items/${id}`);
  },
};
