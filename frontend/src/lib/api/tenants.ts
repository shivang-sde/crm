import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import {
  TenantProvisionRequest,
  TenantProvisionResponse,
  TenantResponse,
  TenantUpdateRequest,
} from "@/types/tenant";

import { unwrapResponse } from "./api-utils";

export const tenantApi = {
  provisionTenant: async (data: TenantProvisionRequest) => {
    const response = await api.post<ApiResponse<TenantProvisionResponse>>(
      "/tenants/provision",
      data
    );
    return unwrapResponse(response);
  },
  getAllTenants: async () => {
    const response = await api.get<ApiResponse<TenantResponse[]>>("/tenants");
    return unwrapResponse(response);
  },
  
  getTenant: async (tenantId: string) => {
    const response = await api.get<ApiResponse<TenantResponse>>(`/tenants/${tenantId}`);
    return unwrapResponse(response);
  },

  updateTenant: async (
    tenantId: string,
    data: TenantUpdateRequest
  ) => {
    const response = await api.put<ApiResponse<TenantResponse>>(`/tenants/${tenantId}`, data);
    return unwrapResponse(response);
  },
};
