'use client';

import { Badge } from '@/components/ui/badge';
import { Clock, AlertTriangle, CheckCircle2, Timer } from 'lucide-react';
import { formatDateTime } from '@/lib/utils';
import type { TicketResponse } from '@/types/tickets';

type SlaState = 'breached' | 'responseOverdue' | 'resolutionOverdue' | 'responded' | 'within';

const slaStateStyles: Record<SlaState, string> = {
  breached: 'bg-red-100 text-red-800',
  responseOverdue: 'bg-orange-100 text-orange-800',
  resolutionOverdue: 'bg-orange-100 text-orange-800',
  responded: 'bg-emerald-100 text-emerald-800',
  within: 'bg-gray-100 text-gray-800',
};

const slaStateLabels: Record<SlaState, string> = {
  breached: 'Breached',
  responseOverdue: 'Response due',
  resolutionOverdue: 'Resolution due',
  responded: 'First response recorded',
  within: 'Within SLA',
};

/**
 * Derives the SLA state purely from backend values.
 *
 * Deliberately not a deadline calculator: the backend owns `slaBreached` and the
 * deadlines, and a breach is only recorded once the scheduler has run. Until then
 * "overdue" is a display hint derived from the stored due timestamps, not a claim
 * that the backend has flagged anything.
 */
export function resolveSlaState(
  ticket: Pick<TicketResponse, 'slaBreached' | 'responseDueAt' | 'resolutionDueAt' | 'firstResponseAt'>,
  now: Date = new Date()
): SlaState {
  if (ticket.slaBreached) {
    return 'breached';
  }
  if (ticket.responseDueAt && new Date(ticket.responseDueAt) < now && !ticket.firstResponseAt) {
    return 'responseOverdue';
  }
  if (ticket.resolutionDueAt && new Date(ticket.resolutionDueAt) < now) {
    return 'resolutionOverdue';
  }
  if (ticket.firstResponseAt) {
    return 'responded';
  }
  return 'within';
}

export function TicketSlaBadge({ ticket }: { ticket: TicketResponse }) {
  const state = resolveSlaState(ticket);
  return (
    <Badge variant="secondary" className={slaStateStyles[state]}>
      {slaStateLabels[state]}
    </Badge>
  );
}

export function TicketSlaPanel({ ticket }: { ticket: TicketResponse }) {
  const state = resolveSlaState(ticket);

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        {state === 'breached' ? (
          <AlertTriangle className="h-4 w-4 text-red-600" />
        ) : state === 'within' ? (
          <CheckCircle2 className="h-4 w-4 text-emerald-600" />
        ) : (
          <Timer className="h-4 w-4 text-orange-600" />
        )}
        <span className="text-sm font-medium">{slaStateLabels[state]}</span>
      </div>

      <dl className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-muted-foreground">Response due</dt>
          <dd className="flex items-center gap-1">
            <Clock className="h-3 w-3 text-muted-foreground" />
            {formatDateTime(ticket.responseDueAt)}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Resolution due</dt>
          <dd className="flex items-center gap-1">
            <Clock className="h-3 w-3 text-muted-foreground" />
            {formatDateTime(ticket.resolutionDueAt)}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">First response</dt>
          <dd className="flex items-center gap-1">
            <Clock className="h-3 w-3 text-muted-foreground" />
            {ticket.firstResponseAt ? formatDateTime(ticket.firstResponseAt) : 'Not yet responded'}
          </dd>
        </div>
      </dl>
    </div>
  );
}
