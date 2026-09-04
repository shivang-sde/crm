import api from "./api";
import { unwrapResponse } from "./api-utils";
import type {
  CallOpeningEvent,
  CallingProviderOption,
  ClickToCallRequest,
  ClickToCallResponse,
} from "@/types/call-opening";
import type { ApiResponse } from "@/types/auth";

export interface CallOpeningDeliveryResponse {
  eventId: string;
  status: string;
  deliveredAt?: string | null;
}

export const callOpeningApi = {
  clickToCall: async (
    request: ClickToCallRequest
  ): Promise<ClickToCallResponse> => {
    const response = await api.post<ApiResponse<ClickToCallResponse>>(
      "/calls/click-to-call",
      request
    );

    return unwrapResponse(response);
  },

  getCallingProviders: async (): Promise<CallingProviderOption[]> => {
    const response = await api.get<ApiResponse<CallingProviderOption[]>>(
      "/calling-providers"
    );
    return unwrapResponse(response);
  },

  getPendingOpeningEvents: async (): Promise<CallOpeningEvent[]> => {
    const response = await api.get<ApiResponse<CallOpeningEvent[]>>(
      "/calls/opening-events/pending"
    );

    return unwrapResponse(response);
  },

  markOpeningEventDelivered: async (
    eventId: string
  ): Promise<CallOpeningDeliveryResponse> => {
    const response = await api.post<
      ApiResponse<CallOpeningDeliveryResponse>
    >(`/calls/opening-events/${eventId}/delivered`);

    return unwrapResponse(response);
  },
};