'use client';

import { useRouter, useParams } from 'next/navigation';
import Link from 'next/link';
import { ArrowLeft, Pencil, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

import { useTicket, useDeleteTicket } from '@/lib/hooks/tickets';
import { useUserLookup } from '@/lib/hooks/useUserLookup';
import { useContact } from '@/lib/hooks/contacts';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { apiErrorMessage } from '@/lib/api/api-utils';
import { formatDateTime } from '@/lib/utils';

import { TicketStatusControl, TicketStatusBadge } from '../_components/TicketStatusControl';
import { TicketSlaPanel } from '../_components/TicketSla';
import { TicketAssignControl } from '../_components/TicketAssignControl';
import { TicketMessages } from '../_components/TicketMessages';
import { TicketTimeline } from '../_components/TicketTimeline';
import { TicketFeedback } from '../_components/TicketFeedback';

export default function TicketDetailPage() {
  const router = useRouter();
  const params = useParams<{ id?: string | string[] }>();
  const rawId = params?.id;
  const id = typeof rawId === 'string' ? rawId : rawId?.[0] ?? '';

  const { canEditTickets, canDeleteTickets, canViewFeedback } = usePermissions();
  const { resolveUserName } = useUserLookup();
  const { data: ticket, isLoading, error } = useTicket(id);
  const { data: contact } = useContact(ticket?.contactId ?? undefined);
  const deleteTicket = useDeleteTicket();

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-2/3" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">This ticket could not be loaded.</p>
      </div>
    );
  }

  if (!ticket) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">Ticket not found</p>
      </div>
    );
  }

  const contactName = contact
    ? [contact.firstName, contact.lastName].filter(Boolean).join(' ') || contact.email || 'Contact'
    : 'View contact';

  const handleDelete = async () => {
    if (!confirm('Delete this ticket? The record is retained but hidden from lists.')) return;
    try {
      await deleteTicket.mutateAsync(ticket.id);
      toast.success('Ticket deleted');
      router.push('/tickets');
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Failed to delete ticket'));
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <Button variant="outline" size="icon" onClick={() => router.push('/tickets')}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <h1 className="text-2xl font-bold tracking-tight">{ticket.subject}</h1>
            <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
              <TicketStatusBadge status={ticket.status} />
              <span>Owner: {resolveUserName(ticket.ownerId)}</span>
            </div>
          </div>
        </div>
        <div className="flex gap-2">
          {canEditTickets && (
            <Button variant="outline" onClick={() => router.push(`/tickets/${ticket.id}/edit`)}>
              <Pencil className="mr-2 h-4 w-4" />
              Edit
            </Button>
          )}
          {canDeleteTickets && (
            <Button variant="destructive" onClick={handleDelete} disabled={deleteTicket.isPending}>
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </Button>
          )}
        </div>
      </div>

      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="conversation">Conversation</TabsTrigger>
          <TabsTrigger value="timeline">Timeline</TabsTrigger>
          {canViewFeedback && <TabsTrigger value="feedback">Feedback</TabsTrigger>}
          <TabsTrigger value="sla">SLA</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-4 pt-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Overview</CardTitle>
              <CardDescription>
                Ticket {ticket.id.slice(0, 8)} · created {formatDateTime(ticket.createdAt)}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="whitespace-pre-wrap text-sm">{ticket.description || 'No description.'}</p>

              <dl className="grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-muted-foreground">Account</dt>
                  <dd>
                    <Link className="hover:underline" href={`/accounts/${ticket.accountId}`}>
                      View account
                    </Link>
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Contact</dt>
                  <dd>
                    {ticket.contactId ? (
                      <Link className="hover:underline" href={`/contacts/${ticket.contactId}`}>
                        {contactName}
                      </Link>
                    ) : (
                      <span className="text-muted-foreground">Not specified</span>
                    )}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Resolved</dt>
                  <dd>{formatDateTime(ticket.resolvedAt)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Closed</dt>
                  <dd>{formatDateTime(ticket.closedAt)}</dd>
                </div>
              </dl>
            </CardContent>
          </Card>

          <TicketStatusControl ticket={ticket} />
          <TicketAssignControl ticket={ticket} />
        </TabsContent>

        <TabsContent value="conversation" className="pt-4">
          <TicketMessages ticket={ticket} />
        </TabsContent>

        <TabsContent value="timeline" className="pt-4">
          <TicketTimeline ticketId={ticket.id} />
        </TabsContent>

        {canViewFeedback && (
          <TabsContent value="feedback" className="pt-4">
            <TicketFeedback ticketId={ticket.id} />
          </TabsContent>
        )}

        <TabsContent value="sla" className="space-y-3 pt-4">
          <TicketSlaPanel ticket={ticket} />
          {ticket.slaBreached && (
            <Badge variant="secondary" className="bg-red-100 text-red-800">
              This ticket breached its SLA. The record is kept even after resolution.
            </Badge>
          )}
        </TabsContent>
      </Tabs>
    </div>
  );
}
