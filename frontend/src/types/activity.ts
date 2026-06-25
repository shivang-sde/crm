import { TaskResponse } from './tasks';
import { CallResponse } from './calls';
import { MeetingResponse } from './meetings';

export type ActivityType = 'TASK' | 'CALL' | 'MEETING';

export interface UnifiedActivity {
  id: string;
  type: ActivityType;
  subject: string;
  description?: string;
  status: string;
  entityType?: string;
  entityId?: string;
  entityName?: string;
  dueDate?: string;
  startTime?: string;
  endTime?: string;
  location?: string;
  phoneNumber?: string;
  callType?: string;
  priority?: string;
  isClosed?: boolean;
  isOverdue?: boolean;
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
  // Raw data for type-specific rendering
  rawData?: TaskResponse | CallResponse | MeetingResponse;
}

export interface UnifiedActivitiesFilters {
  entityType?: string;
  entityId?: string;
  types?: ActivityType[];
  status?: string[];
  startDate?: string;
  endDate?: string;
  assignedToId?: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}
