import { Recurrence } from './recurrence';

export type TaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'WAITING' | 'COMPLETED' | 'CANCELLED';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type EntityType = 'LEAD' | 'CONTACT' | 'ACCOUNT' | 'DEAL' | 'OPPORTUNITY';

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
  isOverdue?: boolean;
}

export interface TaskCreateRequest {
  subject: string;
  description?: string;
  dueDate?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  entityType?: EntityType;
  entityId?: string;
  remindAt?: string;
  recurrence?: Recurrence;
  customData?: Record<string, any>;
  assignedToId?: string;
}

export interface TaskUpdateRequest {
  subject?: string;
  description?: string;
  dueDate?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  remindAt?: string;
  recurrence?: Recurrence;
  customData?: Record<string, any>;
  assignedToId?: string;
}
