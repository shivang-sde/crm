import { Recurrence } from './recurrence';
import { EntityType } from './tasks';

export type MeetingStatus = 'PLANNED' | 'HELD' | 'NOT_HELD' | 'CANCELLED';

export interface MeetingAttendee {
  email?: string;
  contactId?: string;
  name?: string;
  status?: 'ACCEPTED' | 'DECLINED' | 'TENTATIVE' | 'PENDING';
}

export interface MeetingResponse {
  id: string;
  subject: string;
  description?: string;
  location?: string;
  agenda?: string;
  attendees: MeetingAttendee[];
  startTime: string;
  endTime: string;
  status: MeetingStatus;
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

export interface MeetingCreateRequest {
  subject: string;
  description?: string;
  location?: string;
  agenda?: string;
  attendees?: MeetingAttendee[];
  startTime: string;
  endTime: string;
  status?: MeetingStatus;
  entityType?: EntityType;
  entityId?: string;
  remindAt?: string;
  recurrence?: Recurrence;
  customData?: Record<string, any>;
  assignedToId?: string;
}

export interface MeetingUpdateRequest {
  subject?: string;
  description?: string;
  location?: string;
  agenda?: string;
  attendees?: MeetingAttendee[];
  startTime?: string;
  endTime?: string;
  status?: MeetingStatus;
  remindAt?: string;
  recurrence?: Recurrence;
  customData?: Record<string, any>;
  assignedToId?: string;
}
