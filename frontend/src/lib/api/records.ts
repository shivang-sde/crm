import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import {
  RecordFieldCreateRequest,
  RecordFieldResponse,
  RecordFieldUpdateRequest,
  RecordTypeCreateRequest,
  RecordTypeResponse,
  RecordTypeUpdateRequest,
  RecordTypeListMeta,
  DisplayConfigRequest,
  DisplayConfigResponse,
  CrmRecordCreateRequest,
  CrmRecordResponse,
  CrmRecordUpdateRequest,
  CrmRecordListMeta,
  RecordMappingProfileCreateRequest,
  RecordMappingProfileResponse,
  RecordMappingProfileUpdateRequest,
  MappingProfilesListMeta,
  RecordWebhookCreateRequest,
  RecordWebhookResponse,
  RecordWebhookUpdateRequest,
  RecordWebhooksListMeta,
  RecordWebhookDeliveryResponse,
  RecordWebhookDeliveryDetailResponse,
  RecordWebhookDeliveriesListMeta,
} from "@/types/records";
import { unwrapResponse, unwrapListResponse } from "./api-utils";

export const recordTypeApi = {
  list: async (page = 0, size = 20) => {
    const res = await api.get<ApiResponse<RecordTypeResponse[]>>("/record-types", {
      params: { page, size },
    });
    const { data, meta } = unwrapListResponse<RecordTypeResponse, Partial<RecordTypeListMeta>>(res);
    return {
      data,
      meta: {
        page: (meta as any).page ?? page,
        size: (meta as any).size ?? size,
        total: (meta as any).total ?? data.length,
        totalPages: (meta as any).totalPages ?? 1,
      },
    };
  },
  get: async (id: string) => {
    const res = await api.get<ApiResponse<RecordTypeResponse>>(`/record-types/${id}`);
    return unwrapResponse(res);
  },
  create: async (data: RecordTypeCreateRequest) => {
    const res = await api.post<ApiResponse<RecordTypeResponse>>("/record-types", data);
    return unwrapResponse(res);
  },
  update: async (id: string, data: RecordTypeUpdateRequest) => {
    const res = await api.patch<ApiResponse<RecordTypeResponse>>(`/record-types/${id}`, data);
    return unwrapResponse(res);
  },
  delete: async (id: string) => {
    await api.delete(`/record-types/${id}`);
  },
};

export const recordFieldApi = {
  list: async (recordTypeId: string) => {
    const res = await api.get<ApiResponse<RecordFieldResponse[]>>(`/record-types/${recordTypeId}/fields`);
    return unwrapResponse(res);
  },
  create: async (recordTypeId: string, data: RecordFieldCreateRequest) => {
    const res = await api.post<ApiResponse<RecordFieldResponse>>(`/record-types/${recordTypeId}/fields`, data);
    return unwrapResponse(res);
  },
  update: async (recordTypeId: string, fieldId: string, data: RecordFieldUpdateRequest) => {
    const res = await api.patch<ApiResponse<RecordFieldResponse>>(`/record-types/${recordTypeId}/fields/${fieldId}`, data);
    return unwrapResponse(res);
  },
  delete: async (recordTypeId: string, fieldId: string) => {
    await api.delete(`/record-types/${recordTypeId}/fields/${fieldId}`);
  },
};

export const recordDisplayApi = {
  get: async (recordTypeId: string) => {
    const res = await api.get<ApiResponse<DisplayConfigResponse>>(`/record-types/${recordTypeId}/display-config`);
    return unwrapResponse(res);
  },
  put: async (recordTypeId: string, data: DisplayConfigRequest) => {
    const res = await api.put<ApiResponse<DisplayConfigResponse>>(`/record-types/${recordTypeId}/display-config`, data);
    return unwrapResponse(res);
  },
  reset: async (recordTypeId: string) => {
    await api.delete(`/record-types/${recordTypeId}/display-config`);
  },
};

export const recordApi = {
  list: async (params: { recordTypeId?: string; page?: number; size?: number; search?: string; sort?: string; direction?: string } = {}) => {
    const res = await api.get<ApiResponse<CrmRecordResponse[]>>("/records", { params });
    const { data, meta } = unwrapListResponse<CrmRecordResponse, Partial<CrmRecordListMeta>>(res);
    return {
      data,
      meta: {
        page: (meta as any).page ?? params.page ?? 0,
        size: (meta as any).size ?? params.size ?? 20,
        total: (meta as any).total ?? data.length,
        totalPages: (meta as any).totalPages ?? 1,
      },
    };
  },
  get: async (id: string) => {
    const res = await api.get<ApiResponse<CrmRecordResponse>>(`/records/${id}`);
    return unwrapResponse(res);
  },
  create: async (data: CrmRecordCreateRequest) => {
    const res = await api.post<ApiResponse<CrmRecordResponse>>("/records", data);
    return unwrapResponse(res);
  },
  update: async (id: string, data: CrmRecordUpdateRequest) => {
    const res = await api.put<ApiResponse<CrmRecordResponse>>(`/records/${id}`, data);
    return unwrapResponse(res);
  },
  patch: async (id: string, data: Record<string, unknown>) => {
    const res = await api.patch<ApiResponse<CrmRecordResponse>>(`/records/${id}`, data);
    return unwrapResponse(res);
  },
  delete: async (id: string) => {
    await api.delete(`/records/${id}`);
  },
};

export const recordMappingApi = {
  list: async (params: { recordTypeId?: string; page?: number; size?: number } = {}) => {
    const res = await api.get<ApiResponse<RecordMappingProfileResponse[]>>("/record-mapping-profiles", { params });
    const { data, meta } = unwrapListResponse<RecordMappingProfileResponse, Partial<MappingProfilesListMeta>>(res);
    return {
      data,
      meta: {
        page: (meta as any).page ?? params.page ?? 0,
        size: (meta as any).size ?? params.size ?? 20,
        total: (meta as any).total ?? data.length,
        totalPages: (meta as any).totalPages ?? 1,
      },
    };
  },
  get: async (id: string) => {
    const res = await api.get<ApiResponse<RecordMappingProfileResponse>>(`/record-mapping-profiles/${id}`);
    return unwrapResponse(res);
  },
  create: async (data: RecordMappingProfileCreateRequest) => {
    const res = await api.post<ApiResponse<RecordMappingProfileResponse>>("/record-mapping-profiles", data);
    return unwrapResponse(res);
  },
  update: async (id: string, data: RecordMappingProfileUpdateRequest) => {
    const res = await api.patch<ApiResponse<RecordMappingProfileResponse>>(`/record-mapping-profiles/${id}`, data);
    return unwrapResponse(res);
  },
  delete: async (id: string) => {
    await api.delete(`/record-mapping-profiles/${id}`);
  },
  preview: async (id: string, payload: Record<string, unknown>) => {
    const res = await api.post<ApiResponse<{ mappedData: Record<string, unknown> }>>(`/record-mapping-profiles/${id}/preview`, { payload });
    return unwrapResponse(res);
  },
};

export const recordWebhookApi = {
  list: async (params: { recordTypeId?: string; isActive?: boolean; page?: number; size?: number } = {}) => {
    const res = await api.get<ApiResponse<RecordWebhookResponse[]>>("/record-webhooks", { params });
    const { data, meta } = unwrapListResponse<RecordWebhookResponse, Partial<RecordWebhooksListMeta>>(res);
    return {
      data,
      meta: {
        page: (meta as any).page ?? params.page ?? 0,
        size: (meta as any).size ?? params.size ?? 20,
        total: (meta as any).total ?? data.length,
        totalPages: (meta as any).totalPages ?? 1,
      },
    };
  },
  get: async (id: string) => {
    const res = await api.get<ApiResponse<RecordWebhookResponse>>(`/record-webhooks/${id}`);
    return unwrapResponse(res);
  },
  create: async (data: RecordWebhookCreateRequest) => {
    const res = await api.post<ApiResponse<RecordWebhookResponse>>("/record-webhooks", data);
    return unwrapResponse(res);
  },
  update: async (id: string, data: RecordWebhookUpdateRequest) => {
    const res = await api.patch<ApiResponse<RecordWebhookResponse>>(`/record-webhooks/${id}`, data);
    return unwrapResponse(res);
  },
  delete: async (id: string) => {
    await api.delete(`/record-webhooks/${id}`);
  },
  rotateSecret: async (id: string) => {
    const res = await api.post<ApiResponse<{ secret: string; webhook: RecordWebhookResponse }>>(`/record-webhooks/${id}/rotate-secret`);
    const data = unwrapResponse(res as any);
    // Backend returns {secret, webhook} inside data (ApiResponse.data is map with secret+webhook)
    // unwrapResponse returns the map directly
    return data as { secret: string; webhook: RecordWebhookResponse };
  },
};

export const recordWebhookDeliveryApi = {
  list: async (webhookId: string, params: { status?: string; page?: number; size?: number } = {}) => {
    const res = await api.get<ApiResponse<RecordWebhookDeliveryResponse[]>>(`/record-webhooks/${webhookId}/deliveries`, { params });
    const { data, meta } = unwrapListResponse<RecordWebhookDeliveryResponse, Partial<RecordWebhookDeliveriesListMeta>>(res);
    return {
      data,
      meta: {
        page: (meta as any).page ?? params.page ?? 0,
        size: (meta as any).size ?? params.size ?? 20,
        total: (meta as any).total ?? data.length,
        totalPages: (meta as any).totalPages ?? 1,
      },
    };
  },
  get: async (webhookId: string, deliveryId: string) => {
    const res = await api.get<ApiResponse<RecordWebhookDeliveryDetailResponse>>(`/record-webhooks/${webhookId}/deliveries/${deliveryId}`);
    return unwrapResponse(res);
  },
};
