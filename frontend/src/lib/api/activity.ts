import api from './api';
import type { UnifiedActivity, UnifiedActivitiesFilters } from '../../types/activity';
import type { ListResponse } from '../../types/common';

export type { UnifiedActivitiesFilters } from '../../types/activity';

export const activityApi = {
  /**
   * entityType and entityId are required: the generic /activities endpoint now
   * authorizes through the parent record, so an unfiltered tenant-wide listing is
   * rejected. Scope the call to a specific entity.
   */
  getUnifiedActivities: async (
    filters: UnifiedActivitiesFilters & { entityType: string; entityId: string }
  ): Promise<ListResponse<UnifiedActivity>> => {
    const response = await api.get('/activities', { params: filters });
    return response.data;
  },
};
