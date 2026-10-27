import { useQuery } from '@tanstack/react-query';
import { activityApi, type UnifiedActivitiesFilters } from '@/lib/api/activity';

export type { UnifiedActivitiesFilters } from '@/lib/api/activity';

// Requires the entity scope: the generic /activities endpoint authorizes through
// the parent record, so an unscoped listing is rejected.
export function useUnifiedActivities(
  filters: UnifiedActivitiesFilters & { entityType: string; entityId: string }
) {
  return useQuery({
    queryKey: ['activities', filters],
    queryFn: () => activityApi.getUnifiedActivities(filters),
  });
}
