import api from "./api";
import { unwrapResponse } from "./api-utils";
import type {
  CallOpeningEvent,
  ClickToCallRequest,
  ClickToCallResponse,
} from "@/types/call-opening";
import type { ApiResponse } from "@/types/auth";

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

  getPendingOpeningEvents: async (): Promise<CallOpeningEvent[]> => {
    const response = await api.get<ApiResponse<CallOpeningEvent[]>>(
      "/calls/opening-events/pending"
    );
    return unwrapResponse(response);
  },

  markOpeningEventDelivered: async (eventId: string): Promise<void> => {
    const response = await api.post<ApiResponse<void>>(
      `/calls/opening-events/${eventId}/delivered`
    );
    unwrapResponse(response);
  },
};
