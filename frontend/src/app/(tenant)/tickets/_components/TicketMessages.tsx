'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Spinner } from '@/components/ui/spinner';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { MessageSquare, Pencil, Trash2, X } from 'lucide-react';
import { toast } from 'sonner';

import {
  useTicketMessages,
  useCreateTicketMessage,
  useUpdateTicketMessage,
  useDeleteTicketMessage,
} from '@/lib/hooks/tickets';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { useUserLookup } from '@/lib/hooks/useUserLookup';
import { useAuthStore } from '@/lib/store/authStore';
import { apiErrorMessage } from '@/lib/api/api-utils';
import { formatDateTime } from '@/lib/utils';
import type { TicketMessageVisibility, TicketResponse } from '@/types/tickets';

const PAGE_SIZE = 20;

/**
 * Ticket conversation.
 *
 * Author-only editing and deletion mirrors the backend: the caller may only change
 * their own messages, regardless of ticket scope. The control is hidden otherwise,
 * but the backend still rejects the request — hiding is UX, not authorization.
 */
export function TicketMessages({ ticket }: { ticket: TicketResponse }) {
  const { canEditTickets } = usePermissions();
  const { resolveUserName } = useUserLookup();
  const currentUserId = useAuthStore((state) => state.user?.id);

  const [page, setPage] = useState(0);
  const [body, setBody] = useState('');
  const [visibility, setVisibility] = useState<TicketMessageVisibility>('INTERNAL');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingBody, setEditingBody] = useState('');

  const { data, isLoading, error } = useTicketMessages(ticket.id, { page, size: PAGE_SIZE });
  const createMessage = useCreateTicketMessage();
  const updateMessage = useUpdateTicketMessage();
  const deleteMessage = useDeleteTicketMessage();

  const closed = ticket.status === 'CLOSED';
  const canCompose = canEditTickets && !closed;

  const handleCreate = async () => {
    if (!body.trim()) {
      toast.error('Message body is required');
      return;
    }
    try {
      await createMessage.mutateAsync({ id: ticket.id, request: { body, visibility } });
      setBody('');
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Failed to add message'));
    }
  };

  const handleUpdate = async (messageId: string) => {
    try {
      await updateMessage.mutateAsync({
        id: ticket.id,
        messageId,
        request: { body: editingBody },
      });
      setEditingId(null);
      setEditingBody('');
      toast.success('Message updated');
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Failed to update message'));
    }
  };

  const handleDelete = async (messageId: string) => {
    if (!confirm('Delete this message?')) return;
    try {
      await deleteMessage.mutateAsync({ id: ticket.id, messageId });
      toast.success('Message deleted');
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Failed to delete message'));
    }
  };

  const messages = data?.data ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <MessageSquare className="h-4 w-4" />
          Conversation
        </CardTitle>
        <CardDescription>
          {closed
            ? 'This ticket is closed, so the conversation is read-only.'
            : 'Internal notes and customer-visible replies. Customer-visible records intent only — no message is delivered anywhere.'}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {closed && (
          <p className="rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground">
            Messages survive closure but can no longer be added, edited or removed.
          </p>
        )}

        {isLoading && (
          <div className="space-y-2">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
          </div>
        )}

        {error && <p className="text-sm text-red-600">Could not load the conversation.</p>}

        {!isLoading && !error && messages.length === 0 && (
          <p className="text-sm text-muted-foreground">No messages yet.</p>
        )}

        <ul className="space-y-3">
          {messages.map((message) => {
            const isAuthor = message.authorUserId === currentUserId;
            return (
              <li key={message.id} className="rounded-md border p-3">
                <div className="mb-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                  <span className="font-medium text-foreground">
                    {resolveUserName(message.authorUserId)}
                  </span>
                  <span>{formatDateTime(message.createdAt)}</span>
                  <Badge variant="outline" className="text-[10px]">
                    {message.visibility === 'CUSTOMER_VISIBLE'
                      ? 'Customer visible'
                      : 'Internal'}
                  </Badge>
                </div>

                {editingId === message.id ? (
                  <div className="space-y-2">
                    <Textarea
                      value={editingBody}
                      onChange={(e) => setEditingBody(e.target.value)}
                      rows={3}
                    />
                    <div className="flex gap-2">
                      <Button size="sm" onClick={() => handleUpdate(message.id)}>
                        Save
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => setEditingId(null)}>
                        <X className="h-3 w-3" />
                        Cancel
                      </Button>
                    </div>
                  </div>
                ) : (
                  <p className="whitespace-pre-wrap text-sm">{message.body}</p>
                )}

                {canEditTickets && isAuthor && !closed && editingId !== message.id && (
                  <div className="mt-2 flex gap-2">
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        setEditingId(message.id);
                        setEditingBody(message.body);
                      }}
                    >
                      <Pencil className="h-3 w-3" />
                      Edit
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => handleDelete(message.id)}>
                      <Trash2 className="h-3 w-3" />
                      Delete
                    </Button>
                  </div>
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

        {canCompose && (
          <div className="space-y-2 border-t pt-4">
            <Textarea
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="Add a note or reply"
              rows={3}
            />
            <div className="flex items-center justify-between gap-2">
              <Select
                value={visibility}
                onValueChange={(v) => setVisibility(v as TicketMessageVisibility)}
              >
                <SelectTrigger className="w-48">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="INTERNAL">Internal note</SelectItem>
                  <SelectItem value="CUSTOMER_VISIBLE">Customer visible</SelectItem>
                </SelectContent>
              </Select>
              <Button onClick={handleCreate} disabled={createMessage.isPending}>
                {createMessage.isPending && <Spinner className="mr-2 h-3 w-3" />}
                Add message
              </Button>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
