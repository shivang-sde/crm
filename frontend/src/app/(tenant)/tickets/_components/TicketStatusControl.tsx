'use client';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { ArrowRight } from 'lucide-react';
import { toast } from 'sonner';

import { useChangeTicketStatus } from '@/lib/hooks/tickets';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { apiErrorMessage } from '@/lib/api/api-utils';
import { formatLabel } from '@/lib/utils';
import { TICKET_STATUS_TRANSITIONS, type TicketResponse, type TicketStatus } from '@/types/tickets';

/**
 * Status control offering only the transitions the backend accepts.
 *
 * The list mirrors TicketStatus.allowedTransitions so the UI never proposes an
 * illegal move, but the backend still decides — this is a UX affordance, not a
 * second state machine. No optimistic update: the response is the truth.
 */
export function TicketStatusControl({ ticket }: { ticket: TicketResponse }) {
  const { canEditTickets } = usePermissions();
  const changeStatus = useChangeTicketStatus();

  const transitions = TICKET_STATUS_TRANSITIONS[ticket.status] ?? [];

  if (!transitions.length) {
    return (
      <p className="text-sm text-muted-foreground">No further status changes are available.</p>
    );
  }

  const handleChange = async (next: TicketStatus) => {
    try {
      await changeStatus.mutateAsync({ id: ticket.id, request: { status: next } });
      toast.success(`Ticket moved to ${formatLabel(next)}`);
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Failed to change ticket status'));
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Status</CardTitle>
        <CardDescription>Currently {formatLabel(ticket.status)}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {canEditTickets ? (
          <div className="flex flex-wrap gap-2">
            {transitions.map((next) => (
              <Button
                key={next}
                variant="outline"
                size="sm"
                disabled={changeStatus.isPending}
                onClick={() => handleChange(next)}
              >
                {formatLabel(next)}
                <ArrowRight className="ml-2 h-3 w-3" />
              </Button>
            ))}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            You do not have permission to change ticket status.
          </p>
        )}
      </CardContent>
    </Card>
  );
}

export function TicketStatusBadge({ status }: { status: TicketStatus }) {
  const styles: Record<TicketStatus, string> = {
    OPEN: 'bg-gray-100 text-gray-800',
    IN_PROGRESS: 'bg-blue-100 text-blue-800',
    RESOLVED: 'bg-emerald-100 text-emerald-800',
    CLOSED: 'bg-slate-200 text-slate-800',
  };
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${styles[status]}`}
    >
      {formatLabel(status)}
    </span>
  );
}

export function TicketStatusSelect({
  value,
  onChange,
  disabled,
}: {
  value?: TicketStatus;
  onChange: (status: TicketStatus) => void;
  disabled?: boolean;
}) {
  return (
    <Select value={value} onValueChange={(v) => onChange(v as TicketStatus)} disabled={disabled}>
      <SelectTrigger>
        <SelectValue placeholder="Select status" />
      </SelectTrigger>
      <SelectContent>
        {(Object.keys(TICKET_STATUS_TRANSITIONS) as TicketStatus[]).map((status) => (
          <SelectItem key={status} value={status}>
            {formatLabel(status)}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
