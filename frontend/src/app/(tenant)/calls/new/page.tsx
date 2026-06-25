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
import { useCreateCall } from '@/lib/hooks/calls';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { toast } from 'sonner';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

const callSchema = z.object({
  subject: z.string().min(1, 'Subject is required'),
  description: z.string().optional(),
  callType: z.enum(['INCOMING', 'OUTGOING']),
  phoneNumber: z.string().optional(),
  startTime: z.string().optional(),
  endTime: z.string().optional(),
  status: z.enum(['PLANNED', 'HELD', 'NOT_HELD', 'CANCELLED']).optional(),
  entityType: z.enum(['LEAD', 'CONTACT', 'ACCOUNT', 'DEAL']).optional(),
  entityId: z.string().optional(),
  remindAt: z.string().optional(),
  assignedToId: z.string().optional(),
});

type CallFormData = z.infer<typeof callSchema>;

export default function NewCallPage() {
  const router = useRouter();
  const { canEditCalls } = usePermissions();
  const createCall = useCreateCall();

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
    watch,
  } = useForm<CallFormData>({
    resolver: zodResolver(callSchema),
    defaultValues: {
      callType: 'OUTGOING',
      status: 'PLANNED',
    },
  });

  const onSubmit = async (data: CallFormData) => {
    try {
      await createCall.mutateAsync(data);
      toast.success('Call logged successfully');
      router.push('/calls');
    } catch (error) {
      toast.error('Failed to log call');
    }
  };

  if (!canEditCalls) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">You don&apos;t have permission to log calls.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="outline" onClick={() => router.push('/calls')}>
          Back
        </Button>
        <h1 className="text-3xl font-bold tracking-tight">Log New Call</h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Call Details</CardTitle>
          <CardDescription>Fill in the details to log a new call</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">Subject *</label>
                <Input {...register('subject')} placeholder="Enter call subject" />
                {errors.subject && (
                  <p className="text-sm text-red-500">{errors.subject.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">Call Type</label>
                <Select
                  defaultValue={watch('callType') || 'OUTGOING'}
                  onValueChange={(value) => setValue('callType', value as any)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select call type" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="INCOMING">Incoming</SelectItem>
                    <SelectItem value="OUTGOING">Outgoing</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Description</label>
              <Textarea
                {...register('description')}
                placeholder="Enter call notes"
                rows={4}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">Phone Number</label>
                <Input {...register('phoneNumber')} placeholder="+1 (555) 123-4567" />
              </div>

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
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">Start Time</label>
                <Input type="datetime-local" {...register('startTime')} />
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">End Time</label>
                <Input type="datetime-local" {...register('endTime')} />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-4">
              <Button type="button" variant="outline" onClick={() => router.push('/calls')}>
                Cancel
              </Button>
              <Button type="submit" disabled={createCall.isPending}>
                {createCall.isPending ? 'Creating...' : 'Log Call'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
