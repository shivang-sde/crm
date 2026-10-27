import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import {
  TicketActivityResponse,
  TicketAssignRequest,
  TicketCreateRequest,
  TicketListMeta,
  TicketListParams,
  TicketMessageCreateRequest,
  TicketMessageResponse,
  TicketMessageUpdateRequest,
  TicketResponse,
  TicketStatusChangeRequest,
  TicketUpdateRequest,
} from "@/types/tickets";

import { unwrapResponse, unwrapListResponse } from "./api-utils";

const PAGE_META_DEFAULTS = (data: unknown[], meta: Record<string, unknown>) => ({
  page: (meta.page as number) ?? 0,
  size: (meta.size as number) ?? 20,
  total: (meta.total as number) ?? data.length,
  totalPages: (meta.totalPages as number) ?? 1,
});

export const ticketApi = {
  listTickets: async (params: TicketListParams = {}) => {
    const response = await api.get<ApiResponse<TicketResponse[]>>("/tickets", { params });
    const { data, meta } = unwrapListResponse<TicketResponse, TicketListMeta>(response);
    return { data, meta: PAGE_META_DEFAULTS(data, meta as unknown as Record<string, unknown>) };
  },

  getTicket: async (id: string) => {
    const response = await api.get<ApiResponse<TicketResponse>>(`/tickets/${id}`);
    return unwrapResponse(response);
  },

  createTicket: async (request: TicketCreateRequest) => {
    const response = await api.post<ApiResponse<TicketResponse>>("/tickets", request);
    return unwrapResponse(response);
  },

  // PATCH, not PUT: ownership is excluded from the payload on purpose.
  updateTicket: async (id: string, request: TicketUpdateRequest) => {
    const response = await api.patch<ApiResponse<TicketResponse>>(`/tickets/${id}`, request);
    return unwrapResponse(response);
  },

  changeTicketStatus: async (id: string, request: TicketStatusChangeRequest) => {
    const response = await api.post<ApiResponse<TicketResponse>>(`/tickets/${id}/status`, request);
    return unwrapResponse(response);
  },

  // Dedicated assignment endpoint. ticket:write does not imply ticket:assign.
  assignTicket: async (id: string, request: TicketAssignRequest) => {
    const response = await api.post<ApiResponse<TicketResponse>>(`/tickets/${id}/assign`, request);
    return unwrapResponse(response);
  },

  deleteTicket: async (id: string): Promise<void> => {
    await api.delete(`/tickets/${id}`);
  },

  listTicketMessages: async (
    id: string,
    params: { page?: number; size?: number } = {}
  ) => {
    const response = await api.get<ApiResponse<TicketMessageResponse[]>>(
      `/tickets/${id}/messages`,
      { params }
    );
    const { data, meta } = unwrapListResponse<TicketMessageResponse, TicketListMeta>(response);
    return { data, meta: PAGE_META_DEFAULTS(data, meta as unknown as Record<string, unknown>) };
  },

  createTicketMessage: async (id: string, request: TicketMessageCreateRequest) => {
    const response = await api.post<ApiResponse<TicketMessageResponse>>(
      `/tickets/${id}/messages`,
      request
    );
    return unwrapResponse(response);
  },

  updateTicketMessage: async (
    id: string,
    messageId: string,
    request: TicketMessageUpdateRequest
  ) => {
    const response = await api.patch<ApiResponse<TicketMessageResponse>>(
      `/tickets/${id}/messages/${messageId}`,
      request
    );
    return unwrapResponse(response);
  },

  deleteTicketMessage: async (id: string, messageId: string): Promise<void> => {
    await api.delete(`/tickets/${id}/messages/${messageId}`);
  },

  listTicketActivities: async (
    id: string,
    params: { page?: number; size?: number } = {}
  ) => {
    const response = await api.get<ApiResponse<TicketActivityResponse[]>>(
      `/tickets/${id}/activities`,
      { params }
    );
    const { data, meta } = unwrapListResponse<TicketActivityResponse, TicketListMeta>(response);
    return { data, meta: PAGE_META_DEFAULTS(data, meta as unknown as Record<string, unknown>) };
  },
};
