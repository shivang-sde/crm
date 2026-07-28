import { Recurrence } from './recurrence';
import z from 'zod';

export type TaskStatus =
  | 'NOT_STARTED'
  | 'IN_PROGRESS'
  | 'WAITING_ON_SOMEONE'
  | 'DEFERRED'
  | 'COMPLETED';

export type TaskPriority =
  | 'LOW'
  | 'MEDIUM'
  | 'HIGH'
  | 'URGENT';

export type EntityType =
  | 'LEAD'
  | 'CONTACT'
  | 'ACCOUNT'
  | 'DEAL'
  | 'OPPORTUNITY';

export interface AssignedUserResponse {
  id: string;
  name: string;
  email: string;
}

export interface TaskResponse {
  id: string;
  subject: string;
  description?: string;

  dueDate?: string;
  status: TaskStatus;
  priority: TaskPriority;

  entityType?: EntityType;
  entityId?: string;
  entityName?: string;

  remindAt?: string;
  recurrence?: Recurrence;

  completedAt?: string;
  isClosed: boolean;
  isOverdue?: boolean;

  customData?: Record<string, unknown>;

  assignedTo?: AssignedUserResponse;

  createdBy: AssignedUserResponse;

  createdAt: string;
  updatedAt: string;
}

/**
 * Matches the JSON field names expected by TaskCreateRequest in Spring Boot.
 */
export interface TaskCreateRequest {
  subject: string;
  description?: string;

  due_date?: string;

  status?: TaskStatus;
  priority?: TaskPriority;

  entity_type?: EntityType;
  entity_id?: string;

  remind_at?: string;
  recurrence?: Recurrence;

  owner_user_id?: string;
  assigned_to?: string;

  custom_data?: Record<string, unknown>;
}

/**
 * Matches the JSON field names expected by TaskUpdateRequest in Spring Boot.
 */
export interface TaskUpdateRequest {
  subject?: string;
  description?: string;

  due_date?: string;

  status?: TaskStatus;
  priority?: TaskPriority;

  entity_type?: EntityType;
  entity_id?: string;

  remind_at?: string;
  recurrence?: Recurrence;

  owner_user_id?: string;
  assigned_to?: string;

  custom_data?: Record<string, unknown>;
}



export const taskStatusSchema = z.enum([
  'NOT_STARTED',
  'IN_PROGRESS',
  'WAITING_ON_SOMEONE',
  'DEFERRED',
  'COMPLETED',
]);

export const taskPrioritySchema = z.enum([
  'LOW',
  'MEDIUM',
  'HIGH',
  'URGENT',
]);

export const entityTypeSchema = z.enum([
  'LEAD',
  'CONTACT',
  'ACCOUNT',
  'DEAL',
]);

export const taskSchema = z.object({
  subject: z
    .string()
    .trim()
    .min(1, 'Subject is required')
    .max(255, 'Subject must not exceed 255 characters'),

  description: z.string().optional(),

  dueDate: z.string().optional(),

  status: taskStatusSchema,

  priority: taskPrioritySchema,

  entityType: entityTypeSchema.optional(),

  entityId: z.string().uuid('Invalid entity ID').optional().or(z.literal('')),

  remindAt: z.string().optional(),

  ownerUserId: z
    .string()
    .uuid('Invalid owner user ID')
    .optional()
    .or(z.literal('')),

  assignedTo: z
    .string()
    .uuid('Invalid assigned user ID')
    .optional()
    .or(z.literal('')),
});
