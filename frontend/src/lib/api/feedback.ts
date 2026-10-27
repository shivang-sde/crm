import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import {
  FeedbackCreateRequest,
  FeedbackListMeta,
  FeedbackListParams,
  FeedbackRequestCreateRequest,
  FeedbackRequestResponse,
  FeedbackResponse,
  PublicFeedbackRequestResponse,
  PublicFeedbackSubmitRequest,
  PublicFeedbackSubmitResponse,
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

export const feedbackRequestsApi = {
  list: async (ticketId: string) => {
    const response = await api.get<ApiResponse<FeedbackRequestResponse[]>>(
      `/tickets/${ticketId}/feedback-requests`
    );
    return unwrapResponse(response);
  },

  create: async (ticketId: string, request: FeedbackRequestCreateRequest) => {
    const response = await api.post<ApiResponse<FeedbackRequestResponse>>(
      `/tickets/${ticketId}/feedback-requests`,
      request
    );
    return unwrapResponse(response);
  },

  revoke: async (ticketId: string, requestId: string) => {
    const response = await api.post<ApiResponse<FeedbackRequestResponse>>(
      `/tickets/${ticketId}/feedback-requests/${requestId}/revoke`
    );
    return unwrapResponse(response);
  },

  regenerate: async (ticketId: string, requestId: string) => {
    const response = await api.post<ApiResponse<FeedbackRequestResponse>>(
      `/tickets/${ticketId}/feedback-requests/${requestId}/regenerate`
    );
    return unwrapResponse(response);
  },
};

const publicBase = process.env.NEXT_PUBLIC_API_URL ?? "/api/v1";

async function publicJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${publicBase}${path}`, init);
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(body?.error?.message ?? body?.message ?? "Feedback request unavailable");
  }
  if (!body?.success || body.data === undefined) {
    throw new Error("Unexpected API response format");
  }
  return body.data as T;
}

export const publicFeedbackApi = {
  get: (token: string) =>
    publicJson<PublicFeedbackRequestResponse>(`/public/feedback/${encodeURIComponent(token)}`),

  submit: (token: string, request: PublicFeedbackSubmitRequest) =>
    publicJson<PublicFeedbackSubmitResponse>(`/public/feedback/${encodeURIComponent(token)}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    }),
};
