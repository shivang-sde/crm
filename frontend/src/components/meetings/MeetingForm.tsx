import React from 'react';
import { useForm, useFieldArray } from 'react-hook-form';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useCreateMeeting, useUpdateMeeting } from '@/hooks/tasks/useMeetings';
import type { MeetingCreateRequest, MeetingUpdateRequest, MeetingResponse, MeetingAttendee } from '@/types/meetings';
import type { EntityType } from '@/types/tasks';
import { Plus, X } from 'lucide-react';

interface MeetingFormProps {
  meeting?: MeetingResponse;
  entityType?: EntityType;
  entityId?: string;
  onSuccess?: () => void;
  onCancel?: () => void;
}

export function MeetingForm({ meeting, entityType, entityId, onSuccess, onCancel }: MeetingFormProps) {
  const createMutation = useCreateMeeting();
  const updateMutation = useUpdateMeeting();

  const form = useForm<MeetingCreateRequest | MeetingUpdateRequest>({
    defaultValues: {
      subject: meeting?.subject || '',
      description: meeting?.description || '',
      location: meeting?.location || '',
      agenda: meeting?.agenda || '',
      attendees: meeting?.attendees || [],
      startTime: meeting?.startTime ? meeting.startTime.split('T')[0] : '',
      endTime: meeting?.endTime ? meeting.endTime.split('T')[0] : '',
      status: meeting?.status || 'PLANNED',
      entityType: meeting?.entityType || entityType,
      entityId: meeting?.entityId || entityId,
      remindAt: meeting?.remindAt,
      recurrence: meeting?.recurrence,
      assignedToId: meeting?.assignedTo?.id,
    },
  });

  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: 'attendees',
  });

  const onSubmit = (data: MeetingCreateRequest | MeetingUpdateRequest) => {
    if (meeting) {
      updateMutation.mutate(
        { id: meeting.id, request: data as MeetingUpdateRequest },
        { onSuccess }
      );
    } else {
      createMutation.mutate(data as MeetingCreateRequest, { onSuccess });
    }
  };

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
        <FormField
          control={form.control}
          name="subject"
          rules={{ required: 'Subject is required' }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>Subject</FormLabel>
              <FormControl>
                <Input {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="description"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Description</FormLabel>
              <FormControl>
                <Textarea {...field} rows={3} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <div className="grid grid-cols-2 gap-4">
          <FormField
            control={form.control}
            name="location"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Location</FormLabel>
                <FormControl>
                  <Input {...field} placeholder="Address or video link" />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="agenda"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Agenda</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <div className="grid grid-cols-2 gap-4">
          <FormField
            control={form.control}
            name="startTime"
            rules={{ required: 'Start time is required' }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>Start Time</FormLabel>
                <FormControl>
                  <Input type="datetime-local" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="endTime"
            rules={{ required: 'End time is required' }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>End Time</FormLabel>
                <FormControl>
                  <Input type="datetime-local" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <FormField
          control={form.control}
          name="status"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Status</FormLabel>
              <Select onValueChange={field.onChange} defaultValue={field.value}>
                <FormControl>
                  <SelectTrigger>
                    <SelectValue placeholder="Select status" />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  <SelectItem value="PLANNED">Planned</SelectItem>
                  <SelectItem value="HELD">Held</SelectItem>
                  <SelectItem value="NOT_HELD">Not Held</SelectItem>
                  <SelectItem value="CANCELLED">Cancelled</SelectItem>
                </SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          )}
        />

        <div className="space-y-2">
          <FormLabel>Attendees</FormLabel>
          {fields.map((field, index) => (
            <div key={field.id} className="flex gap-2">
              <FormField
                control={form.control}
                name={`attendees.${index}.email`}
                render={({ field }) => (
                  <FormItem className="flex-1">
                    <FormControl>
                      <Input {...field} placeholder="Email address" />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={() => remove(index)}
              >
                <X className="h-4 w-4" />
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => append({ email: '' })}
          >
            <Plus className="h-4 w-4 mr-2" /> Add Attendee
          </Button>
        </div>

        <div className="flex justify-end gap-2 pt-4">
          <Button type="button" variant="outline" onClick={onCancel}>
            Cancel
          </Button>
          <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending}>
            {meeting ? 'Update Meeting' : 'Create Meeting'}
          </Button>
        </div>
      </form>
    </Form>
  );
}
