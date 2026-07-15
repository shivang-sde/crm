'use client';

import { useRouter, useParams } from 'next/navigation';
import { useMeeting } from '@/lib/hooks/meetings';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Spinner } from '@/components/ui/spinner';
import { ArrowLeft, Pencil, Trash2, MapPin, Users } from 'lucide-react';
import { toast } from 'sonner';
import { useDeleteMeeting } from '@/lib/hooks/meetings';

export default function MeetingDetailPage() {
  const router = useRouter();
  const params = useParams<{ id?: string | string[] }>();
  const rawId = params?.id;
  const id = typeof rawId === 'string' ? rawId : rawId?.[0] ?? '';
  const { canEditMeetings, canDeleteMeetings } = usePermissions();
  const { data: meeting, isLoading } = useMeeting(id);
  const deleteMeeting = useDeleteMeeting();

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner />
      </div>
    );
  }

  if (!meeting) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">Meeting not found</p>
      </div>
    );
  }

  const handleDelete = async () => {
    if (!confirm('Are you sure you want to delete this meeting?')) return;
    try {
      await deleteMeeting.mutateAsync(id);
      toast.success('Meeting deleted successfully');
      router.push('/meetings');
    } catch (error) {
      toast.error('Failed to delete meeting');
    }
  };

  const statusColors: Record<string, string> = {
    PLANNED: 'bg-blue-100 text-blue-800',
    HELD: 'bg-green-100 text-green-800',
    NOT_HELD: 'bg-yellow-100 text-yellow-800',
    CANCELLED: 'bg-red-100 text-red-800',
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="outline" size="icon" onClick={() => router.push('/meetings')}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <h1 className="text-3xl font-bold tracking-tight">{meeting.subject}</h1>
        </div>
        <div className="flex items-center gap-2">
          {canEditMeetings && (
            <Button variant="outline" onClick={() => router.push(`/meetings/${id}/edit`)}>
              <Pencil className="mr-2 h-4 w-4" />
              Edit
            </Button>
          )}
          {canDeleteMeetings && (
            <Button variant="destructive" onClick={handleDelete} disabled={deleteMeeting.isPending}>
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </Button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Meeting Details</CardTitle>
            <CardDescription>Basic information about the meeting</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Status</p>
              <Badge className={statusColors[meeting.status]}>
                {meeting.status.replace('_', ' ')}
              </Badge>
            </div>
            {meeting.location && (
              <div>
                <p className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                  <MapPin className="h-4 w-4" />
                  Location
                </p>
                <p className="text-sm">{meeting.location}</p>
              </div>
            )}
            {meeting.attendees && meeting.attendees.length > 0 && (
              <div>
                <p className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                  <Users className="h-4 w-4" />
                  Attendees ({meeting.attendees.length})
                </p>
                <div className="flex flex-wrap gap-2 mt-2">
                  {meeting.attendees.map((attendee: { name?: string; email?: string }, idx: number) => (
                    <Badge key={idx} variant="outline">
                      {attendee.name || attendee.email || 'Unnamed'}
                    </Badge>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Timing</CardTitle>
            <CardDescription>Meeting schedule</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Start Time</p>
              <p className="text-sm font-medium">
                {new Date(meeting.startTime).toLocaleString()}
              </p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">End Time</p>
              <p className="text-sm font-medium">
                {new Date(meeting.endTime).toLocaleString()}
              </p>
            </div>
            {meeting.remindAt && (
              <div>
                <p className="text-sm font-medium text-muted-foreground">Reminder</p>
                <p className="text-sm">{new Date(meeting.remindAt).toLocaleString()}</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {meeting.description && (
        <Card>
          <CardHeader>
            <CardTitle>Description</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm whitespace-pre-wrap">{meeting.description}</p>
          </CardContent>
        </Card>
      )}

      {meeting.agenda && (
        <Card>
          <CardHeader>
            <CardTitle>Agenda</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm whitespace-pre-wrap">{meeting.agenda}</p>
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
              {meeting.assignedTo?.name || meeting.createdBy?.name || 'Unassigned'}
            </p>
          </div>
          <div>
            <p className="text-sm font-medium text-muted-foreground">Linked Entity</p>
            <p className="text-sm">
              {meeting.entityName || 'Not linked'}
              {meeting.entityType && !meeting.entityName && (
                <span className="text-xs text-muted-foreground"> ({meeting.entityType})</span>
              )}
            </p>
          </div>
          <div>
            <p className="text-sm font-medium text-muted-foreground">Created By</p>
            <p className="text-sm">{meeting.createdBy.name}</p>
            <p className="text-xs text-muted-foreground">{meeting.createdBy.email}</p>
          </div>
          <div>
            <p className="text-sm font-medium text-muted-foreground">Created At</p>
            <p className="text-sm">{new Date(meeting.createdAt).toLocaleString()}</p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
