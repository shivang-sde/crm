import api from './api';
import type {
  CallResponse,
  CallCreateRequest,
  CallUpdateRequest,
  CallLinkRequest,
  CallDispositionRequest,
} from '../../types/calls';
import type { ListResponse } from '../../types/common';

export interface CallListParams {
  entityType?: string;
  entityId?: string;
  callType?: string;
  status?: string;
  assignedToId?: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const callApi = {
  listCalls: async (params?: CallListParams): Promise<ListResponse<CallResponse>> => {
    const response = await api.get('/calls', { params });
    return response.data;
  },

  getCall: async (id: string): Promise<CallResponse> => {
    const response = await api.get(`/calls/${id}`);
    return response.data;
  },

  createCall: async (request: CallCreateRequest): Promise<CallResponse> => {
    const response = await api.post('/calls', request);
    return response.data;
  },

  updateCall: async (id: string, request: CallUpdateRequest): Promise<CallResponse> => {
    const response = await api.put(`/calls/${id}`, request);
    return response.data;
  },

  linkCallEntity: async (id: string, request: CallLinkRequest): Promise<CallResponse> => {
    const response = await api.patch(`/calls/${id}/link-entity`, request);
    return response.data;
  },

  saveDisposition: async (id: string, request: CallDispositionRequest): Promise<CallResponse> => {
    const response = await api.patch(`/calls/${id}/disposition`, request);
    return response.data;
  },

  deleteCall: async (id: string): Promise<void> => {
    await api.delete(`/calls/${id}`);
  },
};
