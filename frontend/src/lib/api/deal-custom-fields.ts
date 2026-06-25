import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import { DealCustomFieldCreateRequest, DealCustomFieldResponse } from "@/types/deal-custom-fields";
import { unwrapResponse } from "./api-utils";

export const dealCustomFieldApi = {
  listCustomFields: async () => {
    const response = await api.get<ApiResponse<DealCustomFieldResponse[]>>("/deal-custom-fields");
    return unwrapResponse(response);
  },

  createCustomField: async (payload: DealCustomFieldCreateRequest) => {
    const response = await api.post<ApiResponse<DealCustomFieldResponse>>("/deal-custom-fields", payload);
    return unwrapResponse(response);
  },

  updateCustomField: async (id: string, payload: DealCustomFieldCreateRequest) => {
    const response = await api.put<ApiResponse<DealCustomFieldResponse>>(`/deal-custom-fields/${id}`, payload);
    return unwrapResponse(response);
  },

  deleteCustomField: async (id: string) => {
    await api.delete(`/deal-custom-fields/${id}`);
  },
};
