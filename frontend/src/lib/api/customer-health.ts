import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import {
  RiskSignalAccountParams,
  RiskSignalListMeta,
  RiskSignalListParams,
  RiskSignalResponse,
} from "@/types/customer-health";

import { unwrapListResponse } from "./api-utils";

export const customerHealthApi = {
  listAccountRiskSignals: async (
    accountId: string,
    params: RiskSignalAccountParams = {},
  ) => {
    const response = await api.get<ApiResponse<RiskSignalResponse[]>>(
      `/accounts/${accountId}/health/risk`,
      { params },
    );
    const { data, meta } = unwrapListResponse<
      RiskSignalResponse,
      Partial<{ total: number }>
    >(response);
    return { data, total: meta.total ?? data.length };
  },

  listRiskSignals: async (params: RiskSignalListParams = {}) => {
    const response = await api.get<ApiResponse<RiskSignalResponse[]>>(
      "/customer-health/risk-signals",
      { params },
    );
    const { data, meta } = unwrapListResponse<
      RiskSignalResponse,
      Partial<RiskSignalListMeta>
    >(response);
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
};
