import api from './api';

import type {
  CallResponse,
  CallCreateRequest,
  CallUpdateRequest,
  CallLinkRequest,
  CallDispositionRequest,
  CallType,
  CallStatus,
} from '../../types/calls';

import type { ListResponse } from '../../types/common';
import type { Recurrence } from '../../types/recurrence';
import type { EntityType } from '../../types/tasks';

export interface CallListParams {
  entityType?: string;
  entityId?: string;
  callType?: CallType;
  status?: CallStatus;
  assignedToId?: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * Exact JSON structure returned by Spring Boot.
 */
interface RawCallResponse {
  id: string;
  tenant_id: string | null;

  subject: string;
  description: string | null;

  call_type: CallType;
  phone_number: string | null;

  start_time: string | null;
  end_time: string | null;

  duration_minutes: number | null;
  duration_seconds: number | null;

  disposition: string | null;
  notes: string | null;
  next_action: string | null;

  recording_url: string | null;
  external_call_id: string | null;
  provider_name: string | null;

  follow_up_at: string | null;

  entity_type: string | null;
  entity_id: string | null;
  entity_name: string | null;

  status: CallStatus;

  remind_at: string | null;
  recurrence: Recurrence | null;

  custom_data: Record<string, unknown> | null;

  owner_user_id: string | null;
  assigned_to: string | null;
  assignee_name: string | null;

  created_by: string | null;
  updated_by: string | null;

  created_at: string;
  updated_at: string;
}

interface RawListResponse<T> {
  content: T[];

  empty: boolean;
  first: boolean;
  last: boolean;

  number: number;
  numberOfElements: number;

  size: number;
  totalElements: number;
  totalPages: number;

  pageable?: {
    offset: number;
    pageNumber: number;
    pageSize: number;
    paged: boolean;
    unpaged: boolean;
  };

  sort?: {
    empty: boolean;
    sorted: boolean;
    unsorted: boolean;
  };
}

function mapCallResponse(raw: RawCallResponse): CallResponse {
  return {
    id: raw.id,
    tenantId: raw.tenant_id,

    subject: raw.subject,
    description: raw.description,

    callType: raw.call_type,
    phoneNumber: raw.phone_number,

    startTime: raw.start_time,
    endTime: raw.end_time,

    durationMinutes: raw.duration_minutes,
    durationSeconds: raw.duration_seconds,

    disposition: raw.disposition,
    notes: raw.notes,
    nextAction: raw.next_action,

    recordingUrl: raw.recording_url,
    externalCallId: raw.external_call_id,
    providerName: raw.provider_name,

    followUpAt: raw.follow_up_at,

    entityType: raw.entity_type as EntityType | null,
    entityId: raw.entity_id,
    entityName: raw.entity_name,

    status: raw.status,

    remindAt: raw.remind_at,
    recurrence: raw.recurrence,

    customData: raw.custom_data,

    ownerUserId: raw.owner_user_id,
    assignedTo: raw.assigned_to,
    assigneeName: raw.assignee_name,

    createdBy: raw.created_by,
    updatedBy: raw.updated_by,

    createdAt: raw.created_at,
    updatedAt: raw.updated_at,
  };
}

function mapCallListResponse(
  raw: RawListResponse<RawCallResponse>
): ListResponse<CallResponse> {
  return {
    ...raw,
    content: raw.content.map(mapCallResponse),
  };
}

export const callApi = {
  listCalls: async (
    params?: CallListParams
  ): Promise<ListResponse<CallResponse>> => {
    const response = await api.get<
      RawListResponse<RawCallResponse>
    >('/calls', {
      params,
    });

    return mapCallListResponse(response.data);
  },

  getCall: async (
    id: string
  ): Promise<CallResponse> => {
    const response = await api.get<RawCallResponse>(
      `/calls/${id}`
    );

    return mapCallResponse(response.data);
  },

  createCall: async (
    request: CallCreateRequest
  ): Promise<CallResponse> => {
    const response = await api.post<RawCallResponse>(
      '/calls',
      request
    );

    return mapCallResponse(response.data);
  },

  updateCall: async (
    id: string,
    request: CallUpdateRequest
  ): Promise<CallResponse> => {
    const response = await api.put<RawCallResponse>(
      `/calls/${id}`,
      request
    );

    return mapCallResponse(response.data);
  },

  linkCallEntity: async (
    id: string,
    request: CallLinkRequest
  ): Promise<CallResponse> => {
    const response = await api.patch<RawCallResponse>(
      `/calls/${id}/link-entity`,
      request
    );

    return mapCallResponse(response.data);
  },

  saveDisposition: async (
    id: string,
    request: CallDispositionRequest
  ): Promise<CallResponse> => {
    const response = await api.patch<RawCallResponse>(
      `/calls/${id}/disposition`,
      request
    );

    return mapCallResponse(response.data);
  },

  deleteCall: async (
    id: string
  ): Promise<void> => {
    await api.delete(`/calls/${id}`);
  },
};