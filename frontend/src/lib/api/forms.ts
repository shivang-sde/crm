import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import { unwrapResponse, unwrapListResponse } from "./api-utils";
import type { FormResponse, FormCreateRequest, FormUpdateRequest } from "@/types/forms";

export const formsApi = {
  list: async () => {
    const res = await api.get<ApiResponse<FormResponse[]>>("/forms");
    return unwrapListResponse<FormResponse>(res);
  },
  get: async (id: string) => {
    const res = await api.get<ApiResponse<FormResponse>>(`/forms/${id}`);
    return unwrapResponse(res);
  },
  create: async (data: FormCreateRequest) => {
    const res = await api.post<ApiResponse<FormResponse>>("/forms", data);
    return unwrapResponse(res);
  },
  update: async (id: string, data: FormUpdateRequest) => {
    const res = await api.put<ApiResponse<FormResponse>>(`/forms/${id}`, data);
    return unwrapResponse(res);
  },
  delete: async (id: string) => {
    await api.delete(`/forms/${id}`);
  },
  publish: async (id: string) => {
    const res = await api.post<ApiResponse<FormResponse>>(`/forms/${id}/publish`);
    return unwrapResponse(res);
  },
  unpublish: async (id: string) => {
    const res = await api.post<ApiResponse<FormResponse>>(`/forms/${id}/unpublish`);
    return unwrapResponse(res);
  },
  duplicate: async (id: string) => {
    const res = await api.post<ApiResponse<FormResponse>>(`/forms/${id}/duplicate`);
    return unwrapResponse(res);
  },
};
