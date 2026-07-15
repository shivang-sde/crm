'use client';

import { useRouter, useParams } from 'next/navigation';
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
import { useTask, useUpdateTask } from '@/lib/hooks/tasks';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { toast } from 'sonner';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Spinner } from '@/components/ui/spinner';

const taskSchema = z.object({
  subject: z.string().min(1, 'Subject is required'),
  description: z.string().optional(),
  dueDate: z.string().optional(),
  status: z.enum(['NOT_STARTED', 'IN_PROGRESS', 'WAITING', 'COMPLETED', 'CANCELLED']).optional(),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'URGENT']).optional(),
  entityType: z.enum(['LEAD', 'CONTACT', 'ACCOUNT', 'DEAL', 'OPPORTUNITY']).optional(),
  entityId: z.string().optional(),
  remindAt: z.string().optional(),
  assignedToId: z.string().optional(),
});

type TaskFormData = z.infer<typeof taskSchema>;

export default function EditTaskPage() {
  const router = useRouter();
  const params = useParams<{ id?: string | string[] }>();
  const rawId = params?.id;
  const id = typeof rawId === 'string' ? rawId : rawId?.[0] ?? '';
  const { canEditTasks } = usePermissions();
  const { data: task, isLoading } = useTask(id);
  const updateTask = useUpdateTask();

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
    watch,
  } = useForm<TaskFormData>({
    resolver: zodResolver(taskSchema),
  });

  // Populate form with existing data when task loads
  if (task && !watch('subject')) {
    setValue('subject', task.subject);
    setValue('description', task.description || '');
    setValue('dueDate', task.dueDate ? task.dueDate.slice(0, 16) : '');
    setValue('status', task.status);
    setValue('priority', task.priority);
    setValue('entityType', task.entityType);
    setValue('entityId', task.entityId);
    setValue('remindAt', task.remindAt ? task.remindAt.slice(0, 16) : '');
  }

  const onSubmit = async (data: TaskFormData) => {
    try {
      await updateTask.mutateAsync({ id, request: data });
      toast.success('Task updated successfully');
      router.push(`/tasks/${id}`);
    } catch (error) {
      toast.error('Failed to update task');
    }
  };

  if (!canEditTasks) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">You don&apos;t have permission to edit tasks.</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="outline" onClick={() => router.push(`/tasks/${id}`)}>
          Back
        </Button>
        <h1 className="text-3xl font-bold tracking-tight">Edit Task</h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Task Details</CardTitle>
          <CardDescription>Update the task information</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">Subject *</label>
                <Input {...register('subject')} placeholder="Enter task subject" />
                {errors.subject && (
                  <p className="text-sm text-red-500">{errors.subject.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">Priority</label>
                <Select
                  value={watch('priority') || 'MEDIUM'}
                  onValueChange={(value) => setValue('priority', value as any)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select priority" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="LOW">Low</SelectItem>
                    <SelectItem value="MEDIUM">Medium</SelectItem>
                    <SelectItem value="HIGH">High</SelectItem>
                    <SelectItem value="URGENT">Urgent</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Description</label>
              <Textarea
                {...register('description')}
                placeholder="Enter task description"
                rows={4}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">Due Date</label>
                <Input type="datetime-local" {...register('dueDate')} />
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">Remind At</label>
                <Input type="datetime-local" {...register('remindAt')} />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">Status</label>
                <Select
                  value={watch('status') || 'NOT_STARTED'}
                  onValueChange={(value) => setValue('status', value as any)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select status" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="NOT_STARTED">Not Started</SelectItem>
                    <SelectItem value="IN_PROGRESS">In Progress</SelectItem>
                    <SelectItem value="WAITING">Waiting</SelectItem>
                    <SelectItem value="COMPLETED">Completed</SelectItem>
                    <SelectItem value="CANCELLED">Cancelled</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">Link to Entity</label>
                <Select
                  value={watch('entityType') || ''}
                  onValueChange={(value) => setValue('entityType', value as any)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select entity type" />
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
            </div>

            <div className="flex justify-end gap-2 pt-4">
              <Button type="button" variant="outline" onClick={() => router.push(`/tasks/${id}`)}>
                Cancel
              </Button>
              <Button type="submit" disabled={updateTask.isPending}>
                {updateTask.isPending ? 'Updating...' : 'Update Task'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
