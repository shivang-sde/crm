import { Recurrence } from './recurrence';
import { EntityType } from './tasks';

export type CallType = 'INCOMING' | 'OUTGOING';
export type CallStatus = 'PLANNED' | 'HELD' | 'NOT_HELD' | 'CANCELLED';
export type CallActorType =
  | 'USER'
  | 'SYSTEM'
  | 'WEBHOOK'
  | 'PROVIDER'
  | string;


export interface CallResponse {
  id: string;
  tenantId: string | null;

  subject: string;
  description: string | null;

  callType: CallType;
  phoneNumber: string | null;

  startTime: string | null;
  endTime: string | null;

  durationMinutes: number | null;
  durationSeconds: number | null;

  disposition: string | null;
  notes: string | null;
  nextAction: string | null;
  followUpAt: string | null;

  recordingUrl: string | null;
  externalCallId: string | null;
  providerName: string | null;

  status: CallStatus;

  entityType: EntityType | null;
  entityId: string | null;
  entityName: string | null;

  remindAt: string | null;
  recurrence: Recurrence | null;

  customData: Record<string, unknown> | null;

  ownerUserId: string | null;
  assignedTo: string | null;
  assigneeName: string | null;

  createdBy: string | null;
  updatedBy: string | null;

  createdAt: string;
  updatedAt: string;
}


export interface CallCreateRequest {
  subject: string;
  description?: string;

  callType: CallType;
  phoneNumber?: string;

  startTime?: string;
  endTime?: string;

  status?: CallStatus;

  entityType?: EntityType;
  entityId?: string;

  remindAt?: string;
  recurrence?: Recurrence;

  customData?: Record<string, unknown>;

  assignedToId?: string;
}

export interface CallUpdateRequest {
  subject?: string;
  description?: string;

  callType?: CallType;
  phoneNumber?: string;

  startTime?: string;
  endTime?: string;

  status?: CallStatus;

  remindAt?: string;
  recurrence?: Recurrence;

  customData?: Record<string, unknown>;

  assignedToId?: string;

  entityType?: EntityType;
  entityId?: string;
}

export interface CallLinkRequest {
  entityType: EntityType;
  entityId: string;
}

export interface CallDispositionRequest {
  disposition: string;
  notes?: string;
  nextAction?: string;
  followUpAt?: string;
}


export interface CallPageResponse {
  content: CallResponse[];

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
}

