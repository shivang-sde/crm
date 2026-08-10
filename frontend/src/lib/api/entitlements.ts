import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import {
  CustomerEntitlementListMeta,
  CustomerEntitlementListParams,
  CustomerEntitlementResponse,
  CustomerEntitlementUpdateRequest,
} from "@/types/entitlements";
import { unwrapListResponse, unwrapResponse } from "./api-utils";

export const entitlementApi = {
  listEntitlements: async (params: CustomerEntitlementListParams = {}) => {
    const response = await api.get<ApiResponse<CustomerEntitlementResponse[]>>("/entitlements", { params });
    return unwrapListResponse<CustomerEntitlementResponse, CustomerEntitlementListMeta>(response);
  },

  getEntitlement: async (id: string) => {
    const response = await api.get<ApiResponse<CustomerEntitlementResponse>>(`/entitlements/${id}`);
    return unwrapResponse(response);
  },

  updateEntitlement: async (id: string, data: CustomerEntitlementUpdateRequest) => {
    const response = await api.put<ApiResponse<CustomerEntitlementResponse>>(`/entitlements/${id}`, data);
    return unwrapResponse(response);
  },

  activateEntitlement: async (id: string) => {
    const response = await api.patch<ApiResponse<string>>(`/entitlements/${id}/activate`);
    return unwrapResponse(response);
  },

  suspendEntitlement: async (id: string) => {
    const response = await api.patch<ApiResponse<string>>(`/entitlements/${id}/suspend`);
    return unwrapResponse(response);
  },

  terminateEntitlement: async (id: string) => {
    const response = await api.patch<ApiResponse<string>>(`/entitlements/${id}/terminate`);
    return unwrapResponse(response);
  },
};
