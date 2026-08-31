import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import {
  AnalyticsSummaryResponse,
  AnalyticsTrendPoint,
  AnalyticsDateRange,
  GroupedDataset,
  PipelineStageRow,
  PipelineOwnerRow,
  PipelineAccountRow,
  ConversionOwnerRow,
  DealAgingRow,
  CallStatusSummary,
} from "@/types/analytics";
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

  getPipelineByStage: async (range?: AnalyticsDateRange, tenantId?: string): Promise<PipelineStageRow[]> => {
    const response = await api.get<ApiResponse<PipelineStageRow[]>>(
      "/analytics/pipeline/stage",
      { params: buildParams(range, tenantId) }
    );
    return unwrapResponse(response);
  },

  getPipelineByOwner: async (range?: AnalyticsDateRange, tenantId?: string): Promise<PipelineOwnerRow[]> => {
    const response = await api.get<ApiResponse<PipelineOwnerRow[]>>(
      "/analytics/pipeline/owner",
      { params: buildParams(range, tenantId) }
    );
    return unwrapResponse(response);
  },

  getPipelineByAccount: async (range?: AnalyticsDateRange, tenantId?: string): Promise<PipelineAccountRow[]> => {
    const response = await api.get<ApiResponse<PipelineAccountRow[]>>(
      "/analytics/pipeline/account",
      { params: buildParams(range, tenantId) }
    );
    return unwrapResponse(response);
  },

  getConversionByOwner: async (range?: AnalyticsDateRange, tenantId?: string): Promise<ConversionOwnerRow[]> => {
    const response = await api.get<ApiResponse<ConversionOwnerRow[]>>(
      "/analytics/conversion/owner",
      { params: buildParams(range, tenantId) }
    );
    return unwrapResponse(response);
  },

  getDealAging: async (range?: AnalyticsDateRange, tenantId?: string): Promise<DealAgingRow[]> => {
    const response = await api.get<ApiResponse<DealAgingRow[]>>(
      "/analytics/deals/aging",
      { params: buildParams(range, tenantId) }
    );
    return unwrapResponse(response);
  },

  getCallStatus: async (range?: AnalyticsDateRange, tenantId?: string): Promise<CallStatusSummary> => {
    const response = await api.get<ApiResponse<CallStatusSummary>>(
      "/analytics/calls/status",
      { params: buildParams(range, tenantId) }
    );
    return unwrapResponse(response);
  },

  exportGrouped: async (dataset: GroupedDataset, range?: AnalyticsDateRange, tenantId?: string): Promise<Blob> => {
    const response = await api.get<Blob>("/analytics/export/grouped", {
      params: { dataset, ...buildParams(range, tenantId) },
      responseType: "blob",
    });
    return response.data;
  },
};
