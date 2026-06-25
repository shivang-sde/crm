import React from 'react';
import { useForm } from 'react-hook-form';
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
import { useCreateCall, useUpdateCall } from '@/hooks/tasks/useCalls';
import type { CallCreateRequest, CallUpdateRequest, CallResponse } from '@/types/calls';

interface CallFormProps {
  call?: CallResponse;
  entityType?: string;
  entityId?: string;
  onSuccess?: () => void;
  onCancel?: () => void;
}

export function CallForm({ call, entityType, entityId, onSuccess, onCancel }: CallFormProps) {
  const createMutation = useCreateCall();
  const updateMutation = useUpdateCall();

  const form = useForm<CallCreateRequest | CallUpdateRequest>({
    defaultValues: {
      subject: call?.subject || '',
      description: call?.description || '',
      callType: call?.callType || 'OUTGOING',
      phoneNumber: call?.phoneNumber || '',
      startTime: call?.startTime ? call.startTime.split('T')[0] : '',
      status: call?.status || 'PLANNED',
      entityType: call?.entityType || entityType,
      entityId: call?.entityId || entityId,
      remindAt: call?.remindAt,
      recurrence: call?.recurrence,
      assignedToId: call?.assignedTo?.id,
    },
  });

  const onSubmit = (data: CallCreateRequest | CallUpdateRequest) => {
    if (call) {
      updateMutation.mutate(
        { id: call.id, request: data as CallUpdateRequest },
        { onSuccess }
      );
    } else {
      createMutation.mutate(data as CallCreateRequest, { onSuccess });
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
                <Textarea {...field} rows={4} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <div className="grid grid-cols-2 gap-4">
          <FormField
            control={form.control}
            name="callType"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Call Type</FormLabel>
                <Select onValueChange={field.onChange} defaultValue={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="Select type" />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    <SelectItem value="INCOMING">Incoming</SelectItem>
                    <SelectItem value="OUTGOING">Outgoing</SelectItem>
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="phoneNumber"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Phone Number</FormLabel>
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
            render={({ field }) => (
              <FormItem>
                <FormLabel>Date</FormLabel>
                <FormControl>
                  <Input type="date" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

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
        </div>

        <div className="flex justify-end gap-2 pt-4">
          <Button type="button" variant="outline" onClick={onCancel}>
            Cancel
          </Button>
          <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending}>
            {call ? 'Update Call' : 'Create Call'}
          </Button>
        </div>
      </form>
    </Form>
  );
}
