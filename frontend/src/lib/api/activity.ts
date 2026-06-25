import api from './api';
import type { UnifiedActivity, UnifiedActivitiesFilters } from '../types/activity';
import type { ListResponse } from '../types/common';

export const activityApi = {
  getUnifiedActivities: async (filters?: UnifiedActivitiesFilters): Promise<ListResponse<UnifiedActivity>> => {
    const response = await api.get('/activities', { params: filters });
    return response.data;
  },
};
