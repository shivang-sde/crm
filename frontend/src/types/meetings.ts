import { Recurrence } from './recurrence';
import { EntityType } from './tasks';
import { z } from 'zod';

export type MeetingStatus =
  | 'PLANNED'
  | 'HELD'
  | 'NOT_HELD'
  | 'CANCELLED';

export type MeetingType =
  | 'IN_PERSON'
  | 'VIDEO'
  | 'PHONE';

export type MeetingAttendee = string;

export interface MeetingResponse {
  id: string;
  subject: string;
  description?: string;
  location?: string;
  agenda?: string;

  meetingType?: MeetingType;
  attendees?: MeetingAttendee[];

  startTime: string;
  endTime?: string;

  status: MeetingStatus;

  entityType?: EntityType;
  entityId?: string;
  entityName?: string;

  remindAt?: string;
  recurrence?: Recurrence;

  customData?: Record<string, unknown>;

  ownerUserId?: string;
  assignedTo?: string;
  assigneeName?: string;

  createdBy: string;
  updatedBy?: string;

  createdAt: string;
  updatedAt: string;
}

export interface MeetingCreateRequest {
  subject: string;
  description?: string;
  location?: string;
  agenda?: string;

  meeting_type?: MeetingType;

  start_time: string;
  end_time?: string;

  attendees?: string[];

  entity_type?: EntityType;
  entity_id?: string;

  remind_at?: string;
  recurrence?: Recurrence;

  custom_data?: Record<string, unknown>;
  assigned_to?: string;
}

export interface MeetingUpdateRequest {
  subject?: string;
  description?: string;
  location?: string;
  agenda?: string;

  meeting_type?: MeetingType;

  start_time?: string;
  end_time?: string;

  attendees?: string[];

  entity_type?: EntityType;
  entity_id?: string;

  status?: MeetingStatus;

  remind_at?: string;
  recurrence?: Recurrence;

  custom_data?: Record<string, unknown>;
  assigned_to?: string;
}

const meetingTypes = [
  'IN_PERSON',
  'VIDEO',
  'PHONE',
] as const satisfies readonly MeetingType[];

const meetingStatuses = [
  'PLANNED',
  'HELD',
  'NOT_HELD',
  'CANCELLED',
] as const satisfies readonly MeetingStatus[];

const entityTypes = [
  'LEAD',
  'CONTACT',
  'ACCOUNT',
  'DEAL',
] as const;

export const meetingSchema = z
  .object({
    subject: z
      .string()
      .trim()
      .min(1, 'Subject is required')
      .max(255, 'Subject must not exceed 255 characters'),

    description: z.string().optional(),
    location: z.string().optional(),
    agenda: z.string().optional(),

    meetingType: z.enum(meetingTypes).optional(),

    startTime: z
      .string()
      .min(1, 'Start time is required'),

    endTime: z
      .string()
      .min(1, 'End time is required'),

    entityType: z.enum(entityTypes).optional(),

    entityId: z
      .string()
      .uuid('Invalid entity ID')
      .optional()
      .or(z.literal('')),

    remindAt: z.string().optional(),

    assignedTo: z
      .string()
      .uuid('Invalid assigned user ID')
      .optional()
      .or(z.literal('')),
  })
  .superRefine((data, ctx) => {
    if (!data.startTime || !data.endTime) {
      return;
    }

    const startTime = new Date(data.startTime);
    const endTime = new Date(data.endTime);

    if (
      Number.isNaN(startTime.getTime()) ||
      Number.isNaN(endTime.getTime())
    ) {
      return;
    }

    if (endTime <= startTime) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['endTime'],
        message: 'End time must be later than start time',
      });
    }

    if (data.remindAt) {
      const remindAt = new Date(data.remindAt);

      if (
        !Number.isNaN(remindAt.getTime()) &&
        remindAt >= startTime
      ) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['remindAt'],
          message: 'Reminder must be before start time',
        });
      }
    }

    if (data.entityType && !data.entityId) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['entityId'],
        message: 'Entity ID is required',
      });
    }
  });