import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import { AnalyticsSummaryResponse, AnalyticsTrendPoint, AnalyticsDateRange } from "@/types/analytics";
import { unwrapResponse } from "./api-utils";

export const analyticsApi = {
  getSummary: async (range?: AnalyticsDateRange, tenantId?: string): Promise<AnalyticsSummaryResponse> => {
    const params: Record<string, string> = {};
    if (range?.from) params.from = range.from;
    if (range?.to) params.to = range.to;
    if (tenantId) params.tenantId = tenantId;

    const response = await api.get<ApiResponse<AnalyticsSummaryResponse>>(
      "/analytics/summary",
      { params }
    );
    return unwrapResponse(response);
  },

  getTrends: async (range?: AnalyticsDateRange, tenantId?: string): Promise<AnalyticsTrendPoint[]> => {
    const params: Record<string, string> = {};
    if (range?.from) params.from = range.from;
    if (range?.to) params.to = range.to;
    if (tenantId) params.tenantId = tenantId;

    const response = await api.get<ApiResponse<AnalyticsTrendPoint[]>>(
      "/analytics/trends",
      { params }
    );
    return unwrapResponse(response);
  },
};
