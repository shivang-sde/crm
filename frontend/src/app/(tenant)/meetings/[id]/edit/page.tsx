'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { toast } from 'sonner';
import { X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Spinner } from '@/components/ui/spinner';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Label } from '@/components/ui/label';
import { useMeeting, useUpdateMeeting } from '@/lib/hooks/meetings';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { RecordCombobox } from '@/components/common/RecordCombobox';
import { RecurrencePicker } from '@/components/tasks/RecurrencePicker';
import {
  MeetingUpdateRequest,
  meetingSchema,
} from '@/types/meetings';
import { emptyToUndefined } from '@/lib/utils';

type MeetingFormData = z.infer<typeof meetingSchema>;

export default function EditMeetingPage() {
  const router = useRouter();
  const params = useParams<{ id?: string | string[] }>();
  const id = typeof params?.id === 'string' ? params.id : params?.id?.[0] ?? '';
  const { canEditMeetings } = usePermissions();
  const updateMeeting = useUpdateMeeting();
  const { data: meeting, isLoading: meetingLoading, isError: meetingError } = useMeeting(id);

  const [attendeeInput, setAttendeeInput] = useState('');
  const [attendees, setAttendees] = useState<string[]>([]);

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
    watch,
    reset,
  } = useForm<MeetingFormData>({
    resolver: zodResolver(meetingSchema),
    defaultValues: {
      subject: '',
      description: '',
      location: '',
      agenda: '',
      meetingType: undefined,
      startTime: '',
      endTime: '',
      entityType: undefined,
      entityId: '',
      remindAt: '',
      recurrence: undefined,
      assignedTo: '',
    },
  });

  // Populate the form (and attendee chips) once the meeting has loaded.
  useEffect(() => {
    if (!meeting) return;

    reset({
      subject: meeting.subject ?? '',
      description: meeting.description ?? '',
      location: meeting.location ?? '',
      agenda: meeting.agenda ?? '',
      meetingType: meeting.meetingType,
      startTime: meeting.startTime ? toDateTimeLocal(meeting.startTime) : '',
      endTime: meeting.endTime ? toDateTimeLocal(meeting.endTime) : '',
      entityType: meeting.entityType,
      entityId: meeting.entityId ?? '',
      remindAt: meeting.remindAt ? toDateTimeLocal(meeting.remindAt) : '',
      recurrence: meeting.recurrence,
      assignedTo: meeting.assignedTo ?? '',
    });
    setAttendees([...(meeting.attendees ?? [])]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [meeting]);

  const recurrenceValue = watch('recurrence');
  const entityTypeValue = watch('entityType');

  const addAttendee = () => {
    const email = attendeeInput.trim().toLowerCase();
    if (!email) return;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      toast.error('Invalid attendee email');
      return;
    }
    if (attendees.includes(email)) {
      toast.error('Attendee already added');
      return;
    }
    setAttendees((current) => [...current, email]);
    setAttendeeInput('');
  };

  const removeAttendee = (email: string) => {
    setAttendees((current) => current.filter((attendee) => attendee !== email));
  };

  const onSubmit = async (data: MeetingFormData) => {
    try {
      const payload: MeetingUpdateRequest = {
        subject: data.subject.trim(),
        description: emptyToUndefined(data.description),
        location: emptyToUndefined(data.location),
        agenda: emptyToUndefined(data.agenda),

        meeting_type: data.meetingType,

        start_time: new Date(data.startTime).toISOString(),
        end_time: data.endTime ? new Date(data.endTime).toISOString() : undefined,

        attendees,

        entity_type: data.entityType,
        entity_id: emptyToUndefined(data.entityId),

        status: meeting?.status,
        remind_at: data.remindAt
          ? new Date(data.remindAt).toISOString()
          : undefined,

        recurrence: data.recurrence,
      };

      await updateMeeting.mutateAsync({ id, request: payload });

      toast.success('Meeting updated successfully');
      router.push(`/meetings/${id}`);
    } catch (error) {
      console.error('Failed to update meeting:', error);
      toast.error('Failed to update meeting');
    }
  };

  function toDateTimeLocal(iso: string): string {
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return '';
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
      date.getHours()
    )}:${pad(date.getMinutes())}`;
  }

  if (meetingLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner />
      </div>
    );
  }

  if (meetingError || !meeting) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">Meeting not found</p>
      </div>
    );
  }

  if (!canEditMeetings) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">
          You don&apos;t have permission to edit meetings.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="outline" onClick={() => router.push(`/meetings/${id}`)}>
          Back
        </Button>
        <h1 className="text-3xl font-bold tracking-tight">Edit Meeting</h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Meeting Details</CardTitle>
          <CardDescription>Update the meeting details</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">Subject *</label>
                <Input {...register('subject')} placeholder="Enter meeting subject" />
                {errors.subject && (
                  <p className="text-sm text-red-500">{errors.subject.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">Location</label>
                <Input {...register('location')} placeholder="Meeting room or video link" />
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Meeting Type</label>
              <Select
                value={watch('meetingType') || undefined}
                onValueChange={(value) =>
                  setValue('meetingType', value as MeetingFormData['meetingType'], {
                    shouldDirty: true,
                    shouldValidate: true,
                  })
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select meeting type" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="IN_PERSON">In Person</SelectItem>
                  <SelectItem value="VIDEO">Video</SelectItem>
                  <SelectItem value="PHONE">Phone</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Description</label>
              <Textarea {...register('description')} placeholder="Enter meeting description" rows={3} />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Agenda</label>
              <Textarea {...register('agenda')} placeholder="Enter meeting agenda" rows={3} />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">Start Time *</label>
                <Input type="datetime-local" {...register('startTime')} />
                {errors.startTime && (
                  <p className="text-sm text-red-500">{errors.startTime.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">End Time *</label>
                <Input type="datetime-local" {...register('endTime')} />
                {errors.endTime && (
                  <p className="text-sm text-red-500">{errors.endTime.message}</p>
                )}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">Link to Entity</label>
                <Select
                  value={entityTypeValue || ''}
                  onValueChange={(value) => {
                    setValue('entityId', '', { shouldDirty: true });
                    setValue(
                      'entityType',
                      (value || undefined) as MeetingFormData['entityType'],
                      { shouldDirty: true, shouldValidate: true },
                    );
                  }}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select entity type (optional)" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="">None</SelectItem>
                    <SelectItem value="LEAD">Lead</SelectItem>
                    <SelectItem value="CONTACT">Contact</SelectItem>
                    <SelectItem value="ACCOUNT">Account</SelectItem>
                    <SelectItem value="DEAL">Deal</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">Linked Record</label>
                {entityTypeValue ? (
                  <RecordCombobox
                    entityType={entityTypeValue as 'LEAD' | 'CONTACT' | 'ACCOUNT' | 'DEAL'}
                    value={watch('entityId') || undefined}
                    onChange={(recordId) =>
                      setValue('entityId', recordId ?? '', {
                        shouldDirty: true,
                        shouldValidate: true,
                      })
                    }
                    fallbackLabel={
                      meeting.entityName ||
                      (meeting.entityId
                        ? `Selected ${String(meeting.entityType ?? 'record').toLowerCase()}`
                        : undefined)
                    }
                    placeholder={`Search and link a ${entityTypeValue.toLowerCase()}...`}
                  />
                ) : (
                  <Input placeholder="Select an entity type first" disabled />
                )}
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Attendees</label>
              {attendees.length > 0 && (
                <div className="flex flex-wrap gap-2">
                  {attendees.map((attendee) => (
                    <Badge key={attendee} variant="outline" className="gap-1 pr-1">
                      {attendee}
                      <button
                        type="button"
                        aria-label={`Remove ${attendee}`}
                        className="rounded-full p-0.5 hover:bg-muted"
                        onClick={() => removeAttendee(attendee)}
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </Badge>
                  ))}
                </div>
              )}
              <div className="flex gap-2">
                <Input
                  type="email"
                  placeholder="Enter attendee email..."
                  value={attendeeInput}
                  onChange={(event) => setAttendeeInput(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ',') {
                      event.preventDefault();
                      addAttendee();
                    }
                  }}
                />
                <Button type="button" variant="outline" onClick={addAttendee}>
                  Add
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">
                Press Enter to add each attendee. CRM users and external email addresses are both allowed.
              </p>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">Remind At</label>
                <Input type="datetime-local" {...register('remindAt')} />
              </div>
            </div>

            <div className="space-y-2">
              <Label className="text-sm font-medium">Recurrence</Label>
              <RecurrencePicker
                value={recurrenceValue}
                onChange={(value) =>
                  setValue('recurrence', value, { shouldDirty: true })
                }
              />
            </div>

            <div className="flex justify-end gap-2 pt-4">
              <Button type="button" variant="outline" onClick={() => router.push(`/meetings/${id}`)}>
                Cancel
              </Button>
              <Button type="submit" disabled={updateMeeting.isPending}>
                {updateMeeting.isPending ? 'Saving...' : 'Save Changes'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
