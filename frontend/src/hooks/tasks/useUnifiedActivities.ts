import { useQuery } from '@tanstack/react-query';
import { activityApi, type UnifiedActivitiesFilters } from '@/lib/api/activity';

export type { UnifiedActivitiesFilters } from '@/lib/api/activity';

export function useUnifiedActivities(filters?: UnifiedActivitiesFilters) {
  return useQuery({
    queryKey: ['activities', filters],
    queryFn: () => activityApi.getUnifiedActivities(filters),
  });
}
