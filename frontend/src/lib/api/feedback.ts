import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import {
  FeedbackCreateRequest,
  FeedbackListMeta,
  FeedbackListParams,
  FeedbackResponse,
} from "@/types/feedback";
import { TicketResponse } from "@/types/tickets";

import { unwrapListResponse, unwrapResponse } from "./api-utils";

export const feedbackApi = {
  listFeedback: async (params: FeedbackListParams = {}) => {
    const response = await api.get<ApiResponse<FeedbackResponse[]>>("/feedback", { params });
    const { data, meta } = unwrapListResponse<FeedbackResponse, Partial<FeedbackListMeta>>(response);
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

  createFeedback: async (request: FeedbackCreateRequest) => {
    const response = await api.post<ApiResponse<FeedbackResponse>>("/feedback", request);
    return unwrapResponse(response);
  },

  convertComplaintToTicket: async (feedbackId: string) => {
    const response = await api.post<ApiResponse<TicketResponse>>(
      `/feedback/${feedbackId}/ticket`
    );
    return unwrapResponse(response);
  },
};
