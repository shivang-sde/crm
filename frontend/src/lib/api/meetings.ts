import api from './api';
import type {
  MeetingResponse,
  MeetingCreateRequest,
  MeetingUpdateRequest,
} from '../../types/meetings';
import type { ListResponse } from '../../types/common';

export interface MeetingListParams {
  entityType?: string;
  entityId?: string;
  status?: string;
  assignedToId?: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const meetingApi = {
  listMeetings: async (params?: MeetingListParams): Promise<ListResponse<MeetingResponse>> => {
    const response = await api.get('/meetings', { params });
    return response.data;
  },

  getMeeting: async (id: string): Promise<MeetingResponse> => {
    const response = await api.get(`/meetings/${id}`);
    return response.data;
  },

  createMeeting: async (request: MeetingCreateRequest): Promise<MeetingResponse> => {
    const response = await api.post('/meetings', request);
    return response.data;
  },

  updateMeeting: async (id: string, request: MeetingUpdateRequest): Promise<MeetingResponse> => {
    const response = await api.put(`/meetings/${id}`, request);
    return response.data;
  },

  deleteMeeting: async (id: string): Promise<void> => {
    await api.delete(`/meetings/${id}`);
  },
};
