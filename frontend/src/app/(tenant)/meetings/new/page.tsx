'use client';

import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
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
import { useCreateMeeting } from '@/lib/hooks/meetings';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { toast } from 'sonner';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

const meetingSchema = z.object({
  subject: z.string().min(1, 'Subject is required'),
  description: z.string().optional(),
  location: z.string().optional(),
  agenda: z.string().optional(),
  startTime: z.string(),
  endTime: z.string(),
  status: z.enum(['PLANNED', 'HELD', 'NOT_HELD', 'CANCELLED']).optional(),
  entityType: z.enum(['LEAD', 'CONTACT', 'ACCOUNT', 'DEAL']).optional(),
  entityId: z.string().optional(),
  remindAt: z.string().optional(),
  assignedToId: z.string().optional(),
});

type MeetingFormData = z.infer<typeof meetingSchema>;

export default function NewMeetingPage() {
  const router = useRouter();
  const { canEditMeetings } = usePermissions();
  const createMeeting = useCreateMeeting();

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
    watch,
  } = useForm<MeetingFormData>({
    resolver: zodResolver(meetingSchema),
    defaultValues: {
      status: 'PLANNED',
    },
  });

  const onSubmit = async (data: MeetingFormData) => {
    try {
      await createMeeting.mutateAsync(data);
      toast.success('Meeting scheduled successfully');
      router.push('/meetings');
    } catch (error) {
      toast.error('Failed to schedule meeting');
    }
  };

  if (!canEditMeetings) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">You don&apos;t have permission to schedule meetings.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="outline" onClick={() => router.push('/meetings')}>
          Back
        </Button>
        <h1 className="text-3xl font-bold tracking-tight">Schedule New Meeting</h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Meeting Details</CardTitle>
          <CardDescription>Fill in the details to schedule a new meeting</CardDescription>
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
              <label className="text-sm font-medium">Description</label>
              <Textarea
                {...register('description')}
                placeholder="Enter meeting description"
                rows={3}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Agenda</label>
              <Textarea
                {...register('agenda')}
                placeholder="Enter meeting agenda"
                rows={3}
              />
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
                <label className="text-sm font-medium">Status</label>
                <Select
                  defaultValue={watch('status') || 'PLANNED'}
                  onValueChange={(value) => setValue('status', value as any)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select status" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="PLANNED">Planned</SelectItem>
                    <SelectItem value="HELD">Held</SelectItem>
                    <SelectItem value="NOT_HELD">Not Held</SelectItem>
                    <SelectItem value="CANCELLED">Cancelled</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">Remind At</label>
                <Input type="datetime-local" {...register('remindAt')} />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-4">
              <Button type="button" variant="outline" onClick={() => router.push('/meetings')}>
                Cancel
              </Button>
              <Button type="submit" disabled={createMeeting.isPending}>
                {createMeeting.isPending ? 'Creating...' : 'Schedule Meeting'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
