'use client';

import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { History } from 'lucide-react';

import { useTicketActivities } from '@/lib/hooks/tickets';
import { useUserLookup } from '@/lib/hooks/useUserLookup';
import { formatDateTime, formatLabel } from '@/lib/utils';
import type { TicketActivityMeta, TicketActivityResponse } from '@/types/tickets';

const PAGE_SIZE = 20;

/** Colour hints for the known types. Unknown types fall through to the default. */
const typeStyles: Record<string, string> = {
  TICKET_CREATED: 'bg-emerald-100 text-emerald-800',
  TICKET_STATUS_CHANGED: 'bg-blue-100 text-blue-800',
  TICKET_OWNER_CHANGED: 'bg-violet-100 text-violet-800',
  TICKET_ESCALATED: 'bg-red-100 text-red-800',
  TICKET_SLA_BREACHED: 'bg-red-100 text-red-800',
  TICKET_MESSAGE_CREATED: 'bg-slate-100 text-slate-800',
};

const SYSTEM_ACTIVITIES = new Set(['TICKET_SLA_BREACHED', 'TICKET_ESCALATED']);

const readMeta = (activity: TicketActivityResponse): TicketActivityMeta =>
  (activity.metadata ?? {}) as TicketActivityMeta;

/**
 * Detail line for the metadata the backend actually records.
 *
 * Unknown keys are ignored rather than dumped as raw JSON, and a null previous
 * value renders as a dash so an unassigned-owner transition stays readable.
 */
function describe(activity: TicketActivityResponse): string | null {
  const meta = readMeta(activity);

  if (activity.activityType === 'TICKET_STATUS_CHANGED') {
    if (meta.previousStatus && meta.newStatus) {
      return `${formatLabel(meta.previousStatus)} → ${formatLabel(meta.newStatus)}`;
    }
  }

  if (
    activity.activityType === 'TICKET_OWNER_CHANGED' ||
    activity.activityType === 'TICKET_ESCALATED'
  ) {
    const from = meta.previousOwnerId ?? 'Unassigned';
    if (meta.newOwnerId) {
      return `Owner ${from === 'Unassigned' ? from : from.slice(0, 8)} → ${meta.newOwnerId.slice(0, 8)}`;
    }
  }

  if (activity.activityType === 'TICKET_SLA_BREACHED' && meta.breachType) {
    return `${formatLabel(meta.breachType)} deadline missed`;
  }

  if (activity.activityType === 'TICKET_UPDATED' && meta.changedFields?.length) {
    return `Changed: ${meta.changedFields.map(formatLabel).join(', ')}`;
  }

  if (activity.activityType === 'TICKET_MESSAGE_CREATED' && meta.visibility) {
    return `${formatLabel(meta.visibility)} message`;
  }

  return null;
}

export function TicketTimeline({ ticketId }: { ticketId: string }) {
  const [page, setPage] = useState(0);
  const { resolveUserName } = useUserLookup();
  const { data, isLoading, error } = useTicketActivities(ticketId, { page, size: PAGE_SIZE });

  const activities = data?.data ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <History className="h-4 w-4" />
          Timeline
        </CardTitle>
        <CardDescription>
          Lifecycle, conversation and automated system events, newest first.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {isLoading && (
          <div className="space-y-2">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        )}

        {error && <p className="text-sm text-red-600">Could not load the timeline.</p>}

        {!isLoading && !error && activities.length === 0 && (
          <p className="text-sm text-muted-foreground">No activity recorded yet.</p>
        )}

        <ul className="space-y-2">
          {activities.map((activity) => {
            const isSystem =
              activity.actorType === 'SYSTEM' || SYSTEM_ACTIVITIES.has(activity.activityType);
            const detail = describe(activity);

            return (
              <li key={activity.id} className="rounded-md border p-3">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge
                    variant="secondary"
                    className={typeStyles[activity.activityType] ?? 'bg-gray-100 text-gray-800'}
                  >
                    {formatLabel(activity.activityType.replace(/^TICKET_/, ''))}
                  </Badge>
                  <span className="text-xs text-muted-foreground">
                    {formatDateTime(activity.createdAt)}
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {/* performedBy is null for system events, so actorType decides. */}
                    {isSystem ? 'System' : resolveUserName(activity.performedBy)}
                  </span>
                </div>
                {detail && <p className="mt-1 text-sm">{detail}</p>}
                {activity.description && (
                  <p className="mt-1 text-sm text-muted-foreground">{activity.description}</p>
                )}
              </li>
            );
          })}
        </ul>

        {(data?.meta?.totalPages ?? 1) > 1 && (
          <div className="flex items-center justify-between text-sm">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </Button>
            <span className="text-muted-foreground">
              Page {page + 1} of {data?.meta?.totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={page + 1 >= (data?.meta?.totalPages ?? 1)}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
