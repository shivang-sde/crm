import api from './api';
import type {
  TaskResponse,
  TaskCreateRequest,
  TaskUpdateRequest,
} from '../../types/tasks';
import type { ListResponse } from '../../types/common';

export interface TaskListParams {
  entityType?: string;
  entityId?: string;
  status?: string;
  priority?: string;
  assignedToId?: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const taskApi = {
  listTasks: async (params?: TaskListParams): Promise<ListResponse<TaskResponse>> => {
    const response = await api.get('/tasks', { params });
    return response.data;
  },

  getTask: async (id: string): Promise<TaskResponse> => {
    const response = await api.get(`/tasks/${id}`);
    return response.data;
  },

  createTask: async (request: TaskCreateRequest): Promise<TaskResponse> => {
    const response = await api.post('/tasks', request);
    return response.data;
  },

  updateTask: async (id: string, request: TaskUpdateRequest): Promise<TaskResponse> => {
    const response = await api.put(`/tasks/${id}`, request);
    return response.data;
  },

  deleteTask: async (id: string): Promise<void> => {
    await api.delete(`/tasks/${id}`);
  },

  completeTask: async (id: string): Promise<TaskResponse> => {
    const response = await api.patch(`/tasks/${id}/complete`);
    return response.data;
  },

  reopenTask: async (id: string): Promise<TaskResponse> => {
    const response = await api.patch(`/tasks/${id}/reopen`);
    return response.data;
  },
};
