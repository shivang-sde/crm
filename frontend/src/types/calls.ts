import { Recurrence } from './recurrence';
import { EntityType } from './tasks';

export type CallType = 'INCOMING' | 'OUTGOING';
export type CallStatus = 'PLANNED' | 'HELD' | 'NOT_HELD' | 'CANCELLED';

export interface CallResponse {
  id: string;
  subject: string;
  description?: string;
  callType: CallType;
  phoneNumber?: string;
  startTime?: string;
  endTime?: string;
  durationMinutes?: number;
  status: CallStatus;
  entityType?: EntityType;
  entityId?: string;
  entityName?: string;
  remindAt?: string;
  recurrence?: Recurrence;
  customData?: Record<string, any>;
  assignedTo?: {
    id: string;
    name: string;
    email: string;
  };
  createdBy: {
    id: string;
    name: string;
    email: string;
  };
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
  customData?: Record<string, any>;
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
  customData?: Record<string, any>;
  assignedToId?: string;
}
