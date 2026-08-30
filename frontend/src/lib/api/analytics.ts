import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import { AnalyticsSummaryResponse, AnalyticsTrendPoint, AnalyticsDateRange } from "@/types/analytics";
import { unwrapResponse } from "./api-utils";

function buildParams(range?: AnalyticsDateRange, tenantId?: string): Record<string, string> {
  const params: Record<string, string> = {};
  if (range?.from) params.from = range.from;
  if (range?.to) params.to = range.to;
  if (tenantId) params.tenantId = tenantId;
  return params;
}

export const analyticsApi = {
  getSummary: async (range?: AnalyticsDateRange, tenantId?: string): Promise<AnalyticsSummaryResponse> => {
    const response = await api.get<ApiResponse<AnalyticsSummaryResponse>>(
      "/analytics/summary",
      { params: buildParams(range, tenantId) }
    );
    return unwrapResponse(response);
  },

  getTrends: async (range?: AnalyticsDateRange, tenantId?: string): Promise<AnalyticsTrendPoint[]> => {
    const response = await api.get<ApiResponse<AnalyticsTrendPoint[]>>(
      "/analytics/trends",
      { params: buildParams(range, tenantId) }
    );
    return unwrapResponse(response);
  },

  exportSummary: async (range?: AnalyticsDateRange, tenantId?: string): Promise<Blob> => {
    const response = await api.get<Blob>("/analytics/export/summary", {
      params: buildParams(range, tenantId),
      responseType: "blob",
    });
    return response.data;
  },

  exportTrends: async (range?: AnalyticsDateRange, tenantId?: string): Promise<Blob> => {
    const response = await api.get<Blob>("/analytics/export/trends", {
      params: buildParams(range, tenantId),
      responseType: "blob",
    });
    return response.data;
  },
};
