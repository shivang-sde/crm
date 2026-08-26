import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import { unwrapResponse, unwrapListResponse } from "./api-utils";
import {
  LeadIngestionConfigCreateRequest,
  LeadIngestionConfigResponse,
  LeadIngestionConfigUpdateRequest,
  LeadIngestionFieldMappingRequest,
  LeadIngestionFieldMappingResponse,
  LeadIngestionSourceField,
  LeadIngestionTargetField,
  LeadIngestionEventDetailResponse,
  LeadIngestionEventListMeta,
  LeadIngestionEventListParams,
  LeadIngestionEventSummaryResponse,
  MappedLeadData,
  ValidatedLeadIngestionData,
} from "@/types/acquisition";

export const acquisitionApi = {
  listConfigs: async () => {
    const response = await api.get<ApiResponse<LeadIngestionConfigResponse[]>>(
      "/acquisition/configs"
    );
    return unwrapListResponse<LeadIngestionConfigResponse>(response);
  },

  getConfig: async (id: string) => {
    const response = await api.get<ApiResponse<LeadIngestionConfigResponse>>(
      `/acquisition/configs/${id}`
    );
    return unwrapResponse(response);
  },

  createConfig: async (data: LeadIngestionConfigCreateRequest) => {
    const response = await api.post<ApiResponse<LeadIngestionConfigResponse>>(
      "/acquisition/configs",
      data
    );
    return unwrapResponse(response);
  },

  updateConfig: async (id: string, data: LeadIngestionConfigUpdateRequest) => {
    const response = await api.put<ApiResponse<LeadIngestionConfigResponse>>(
      `/acquisition/configs/${id}`,
      data
    );
    return unwrapResponse(response);
  },

  deleteConfig: async (id: string): Promise<void> => {
    await api.delete(`/acquisition/configs/${id}`);
  },

  listMappings: async (configId: string) => {
    const response = await api.get<
      ApiResponse<LeadIngestionFieldMappingResponse[]>
    >(`/acquisition/configs/${configId}/mappings`);
    return unwrapListResponse<LeadIngestionFieldMappingResponse>(response);
  },

  createMapping: async (
    configId: string,
    data: LeadIngestionFieldMappingRequest
  ) => {
    const response = await api.post<
      ApiResponse<LeadIngestionFieldMappingResponse>
    >(`/acquisition/configs/${configId}/mappings`, data);
    return unwrapResponse(response);
  },

  updateMapping: async (
    configId: string,
    mappingId: string,
    data: LeadIngestionFieldMappingRequest
  ) => {
    const response = await api.put<
      ApiResponse<LeadIngestionFieldMappingResponse>
    >(`/acquisition/configs/${configId}/mappings/${mappingId}`, data);
    return unwrapResponse(response);
  },

  deleteMapping: async (configId: string, mappingId: string): Promise<void> => {
    await api.delete(`/acquisition/configs/${configId}/mappings/${mappingId}`);
  },

  getSourceFields: async (configId: string, eventId: string) => {
    const response = await api.get<ApiResponse<LeadIngestionSourceField[]>>(
      `/acquisition/configs/${configId}/events/${eventId}/source-fields`
    );
    return unwrapListResponse<LeadIngestionSourceField>(response);
  },

  getTargetFields: async (configId: string) => {
    const response = await api.get<ApiResponse<LeadIngestionTargetField[]>>(
      `/acquisition/configs/${configId}/target-fields`
    );
    return unwrapListResponse<LeadIngestionTargetField>(response);
  },

  previewMapping: async (configId: string, eventId: string) => {
    const response = await api.post<ApiResponse<MappedLeadData>>(
      `/acquisition/configs/${configId}/events/${eventId}/preview`
    );
    return unwrapResponse(response);
  },

  listEvents: async (configId: string, params: LeadIngestionEventListParams = {}) => {
    const response = await api.get<ApiResponse<LeadIngestionEventSummaryResponse[]>>(
      `/acquisition/configs/${configId}/events`,
      { params }
    );
    return unwrapListResponse<LeadIngestionEventSummaryResponse, LeadIngestionEventListMeta>(response);
  },

  getEvent: async (configId: string, eventId: string) => {
    const response = await api.get<ApiResponse<LeadIngestionEventDetailResponse>>(
      `/acquisition/configs/${configId}/events/${eventId}`
    );
    return unwrapResponse(response);
  },
  validatePreview: async (configId: string, eventId: string) => {
    const response = await api.post<ApiResponse<ValidatedLeadIngestionData>>(
      `/acquisition/configs/${configId}/events/${eventId}/validate-preview`
    );
    return unwrapResponse(response);
  },
};
