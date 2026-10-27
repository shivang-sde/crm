'use client';

import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { UserCheck } from 'lucide-react';

import { useUserLookup } from '@/lib/hooks/useUserLookup';
import { useAssignTicket } from '@/lib/hooks/tickets';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { apiErrorMessage } from '@/lib/api/api-utils';
import { toast } from 'sonner';
import type { TicketResponse } from '@/types/tickets';

/**
 * Assignment control.
 *
 * Uses POST /tickets/{id}/assign because the Ticket PATCH deliberately cannot
 * change ownership (backend L3.1-P2). Rendered only for ticket:assign holders;
 * hiding it is UX, and the backend rejects the call regardless.
 */
export function TicketAssignControl({ ticket }: { ticket: TicketResponse }) {
  const { canAssignTickets } = usePermissions();
  const { users } = useUserLookup();
  const assignTicket = useAssignTicket();

  if (!canAssignTickets) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <UserCheck className="h-4 w-4" />
        You do not have permission to reassign this ticket.
      </div>
    );
  }

  const handleAssign = async (ownerId: string) => {
    try {
      await assignTicket.mutateAsync({ id: ticket.id, request: { ownerId } });
      toast.success('Ticket reassigned');
    } catch (error) {
      // Same-owner assignment is a backend no-op; do not invent a success message.
      toast.error(apiErrorMessage(error, 'Failed to reassign ticket'));
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <UserCheck className="h-4 w-4" />
          Assignment
        </CardTitle>
        <CardDescription>
          Reassignment is a separate operation from editing ticket details.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <Select
          value={ticket.ownerId ?? undefined}
          onValueChange={handleAssign}
          disabled={assignTicket.isPending}
        >
          <SelectTrigger>
            <SelectValue placeholder="Select an agent" />
          </SelectTrigger>
          <SelectContent>
            {users.map((user) => (
              <SelectItem key={user.id} value={user.id}>
                {[user.firstName, user.lastName].filter(Boolean).join(' ') || user.email || user.id}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </CardContent>
    </Card>
  );
}
