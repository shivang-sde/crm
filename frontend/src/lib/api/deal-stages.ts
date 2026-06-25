import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import { DealStageSummary, DealStageCreateRequest } from "@/types/deal-stages";
import { unwrapResponse } from "./api-utils";

export const dealStageApi = {
  listStages: async () => {
    const response = await api.get<ApiResponse<DealStageSummary[]>>("/deal-stages");
    return unwrapResponse(response);
  },

  createStage: async (payload: DealStageCreateRequest) => {
    const response = await api.post<ApiResponse<DealStageSummary>>("/deal-stages", payload);
    return unwrapResponse(response);
  },

  updateStage: async (id: string, payload: DealStageCreateRequest) => {
    const response = await api.put<ApiResponse<DealStageSummary>>(`/deal-stages/${id}`, payload);
    return unwrapResponse(response);
  },

  deleteStage: async (id: string) => {
    await api.delete(`/deal-stages/${id}`);
  },
};
