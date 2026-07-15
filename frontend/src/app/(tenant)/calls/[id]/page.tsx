'use client';

import { useRouter, useParams } from 'next/navigation';
import { useCall } from '@/lib/hooks/calls';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Spinner } from '@/components/ui/spinner';
import { ArrowLeft, Pencil, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { useDeleteCall } from '@/lib/hooks/calls';

export default function CallDetailPage() {
  const router = useRouter();
  const params = useParams<{ id?: string | string[] }>();
  const rawId = params?.id;
  const id = typeof rawId === 'string' ? rawId : rawId?.[0] ?? '';
  const { canEditCalls, canDeleteCalls } = usePermissions();
  const { data: call, isLoading } = useCall(id);
  const deleteCall = useDeleteCall();

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner />
      </div>
    );
  }

  if (!call) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">Call not found</p>
      </div>
    );
  }

  const handleDelete = async () => {
    if (!confirm('Are you sure you want to delete this call?')) return;
    try {
      await deleteCall.mutateAsync(id);
      toast.success('Call deleted successfully');
      router.push('/calls');
    } catch (error) {
      toast.error('Failed to delete call');
    }
  };

  const statusColors: Record<string, string> = {
    PLANNED: 'bg-blue-100 text-blue-800',
    HELD: 'bg-green-100 text-green-800',
    NOT_HELD: 'bg-yellow-100 text-yellow-800',
    CANCELLED: 'bg-red-100 text-red-800',
  };

  const typeColors: Record<string, string> = {
    INCOMING: 'bg-green-100 text-green-800',
    OUTGOING: 'bg-blue-100 text-blue-800',
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="outline" size="icon" onClick={() => router.push('/calls')}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <h1 className="text-3xl font-bold tracking-tight">{call.subject}</h1>
        </div>
        <div className="flex items-center gap-2">
          {canEditCalls && (
            <Button variant="outline" onClick={() => router.push(`/calls/${id}/edit`)}>
              <Pencil className="mr-2 h-4 w-4" />
              Edit
            </Button>
          )}
          {canDeleteCalls && (
            <Button variant="destructive" onClick={handleDelete} disabled={deleteCall.isPending}>
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </Button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Call Details</CardTitle>
            <CardDescription>Basic information about the call</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Status</p>
              <Badge className={statusColors[call.status]}>
                {call.status.replace('_', ' ')}
              </Badge>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Call Type</p>
              <Badge className={typeColors[call.callType]}>
                {call.callType}
              </Badge>
            </div>
            {call.phoneNumber && (
              <div>
                <p className="text-sm font-medium text-muted-foreground">Phone Number</p>
                <p className="text-sm">{call.phoneNumber}</p>
              </div>
            )}
            <div>
              <p className="text-sm font-medium text-muted-foreground">Duration</p>
              <p className="text-sm">{call.durationMinutes || 0} minutes</p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Timing</CardTitle>
            <CardDescription>Call schedule and timestamps</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Start Time</p>
              <p className="text-sm">
                {call.startTime ? new Date(call.startTime).toLocaleString() : 'Not set'}
              </p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">End Time</p>
              <p className="text-sm">
                {call.endTime ? new Date(call.endTime).toLocaleString() : 'Not set'}
              </p>
            </div>
            {call.remindAt && (
              <div>
                <p className="text-sm font-medium text-muted-foreground">Reminder</p>
                <p className="text-sm">{new Date(call.remindAt).toLocaleString()}</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {call.description && (
        <Card>
          <CardHeader>
            <CardTitle>Description</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm whitespace-pre-wrap">{call.description}</p>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Assignment & Linking</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-4">
          <div>
            <p className="text-sm font-medium text-muted-foreground">Assigned To</p>
            <p className="text-sm">
              {call.assignedTo?.name || call.createdBy?.name || 'Unassigned'}
            </p>
          </div>
          <div>
            <p className="text-sm font-medium text-muted-foreground">Linked Entity</p>
            <p className="text-sm">
              {call.entityName || 'Not linked'}
              {call.entityType && !call.entityName && (
                <span className="text-xs text-muted-foreground"> ({call.entityType})</span>
              )}
            </p>
          </div>
          <div>
            <p className="text-sm font-medium text-muted-foreground">Created By</p>
            <p className="text-sm">{call.createdBy.name}</p>
            <p className="text-xs text-muted-foreground">{call.createdBy.email}</p>
          </div>
          <div>
            <p className="text-sm font-medium text-muted-foreground">Created At</p>
            <p className="text-sm">{new Date(call.createdAt).toLocaleString()}</p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
