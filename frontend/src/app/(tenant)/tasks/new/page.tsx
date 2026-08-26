'use client';

import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { toast } from 'sonner';

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
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';

import { toIsoString, emptyToUndefined } from '@/lib/utils';
import { useCreateTask } from '@/lib/hooks/tasks';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { RecordCombobox } from '@/components/common/RecordCombobox';
import { TaskCreateRequest, taskSchema } from '@/types/tasks';

type TaskFormData = z.infer<typeof taskSchema>;


export default function NewTaskPage() {
  const router = useRouter();
  const { canEditTasks } = usePermissions();
  const createTask = useCreateTask();

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
    watch,
  } = useForm<TaskFormData>({
    resolver: zodResolver(taskSchema),
    defaultValues: {
      subject: '',
      description: '',
      dueDate: '',
      remindAt: '',
      entityId: '',
      ownerUserId: '',
      status: 'NOT_STARTED',
      priority: 'MEDIUM',
    },
  });

  const status = watch('status');
  const priority = watch('priority');
  const entityType = watch('entityType');

  const onSubmit = async (data: TaskFormData) => {
  try {
    const payload: TaskCreateRequest = {
      subject: data.subject.trim(),
      description: emptyToUndefined(data.description),
      due_date: toIsoString(data.dueDate),
      status: data.status,
      priority: data.priority,
      entity_type: data.entityType,
      entity_id: emptyToUndefined(data.entityId),
      remind_at: toIsoString(data.remindAt),
      owner_user_id: emptyToUndefined(data.ownerUserId),
    };

    await createTask.mutateAsync(payload);

    toast.success('Task created successfully');
    router.push('/tasks');
  } catch (error) {
    console.error('Failed to create task:', error);
    toast.error('Failed to create task');
  }
};

  if (!canEditTasks) {
    return (
      <div className="flex h-64 items-center justify-center">
        <p className="text-muted-foreground">
          You don&apos;t have permission to create tasks.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button
          type="button"
          variant="outline"
          onClick={() => router.push('/tasks')}
        >
          Back
        </Button>

        <div>
          <h1 className="text-3xl font-bold tracking-tight">
            Create New Task
          </h1>
          <p className="text-sm text-muted-foreground">
            Create and assign a task to your CRM team.
          </p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Task Details</CardTitle>
          <CardDescription>
            Fill in the details to create a new task.
          </CardDescription>
        </CardHeader>

        <CardContent>
          <form
            onSubmit={handleSubmit(onSubmit)}
            className="space-y-6"
            noValidate
          >
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <label
                  htmlFor="subject"
                  className="text-sm font-medium"
                >
                  Subject *
                </label>

                <Input
                  id="subject"
                  {...register('subject')}
                  placeholder="Enter task subject"
                  aria-invalid={Boolean(errors.subject)}
                />

                {errors.subject && (
                  <p className="text-sm text-destructive">
                    {errors.subject.message}
                  </p>
                )}
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">
                  Priority
                </label>

                <Select
                  value={priority}
                  onValueChange={(value) =>
                    setValue(
                      'priority',
                      value as TaskFormData['priority'],
                      {
                        shouldDirty: true,
                        shouldValidate: true,
                      },
                    )
                  }
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
              <label
                htmlFor="description"
                className="text-sm font-medium"
              >
                Description
              </label>

              <Textarea
                id="description"
                {...register('description')}
                placeholder="Enter task description"
                rows={4}
              />
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <label
                  htmlFor="dueDate"
                  className="text-sm font-medium"
                >
                  Due Date
                </label>

                <Input
                  id="dueDate"
                  type="datetime-local"
                  {...register('dueDate')}
                />
              </div>

              <div className="space-y-2">
                <label
                  htmlFor="remindAt"
                  className="text-sm font-medium"
                >
                  Remind At
                </label>

                <Input
                  id="remindAt"
                  type="datetime-local"
                  {...register('remindAt')}
                />
              </div>
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <label className="text-sm font-medium">
                  Status
                </label>

                <Select
                  value={status}
                  onValueChange={(value) =>
                    setValue(
                      'status',
                      value as TaskFormData['status'],
                      {
                        shouldDirty: true,
                        shouldValidate: true,
                      },
                    )
                  }
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select status" />
                  </SelectTrigger>

                  <SelectContent>
                    <SelectItem value="NOT_STARTED">
                      Not Started
                    </SelectItem>

                    <SelectItem value="IN_PROGRESS">
                      In Progress
                    </SelectItem>

                    <SelectItem value="WAITING_ON_SOMEONE">
                      Waiting on Someone
                    </SelectItem>

                    <SelectItem value="DEFERRED">
                      Deferred
                    </SelectItem>

                    <SelectItem value="COMPLETED">
                      Completed
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">
                  Link to Entity
                </label>

                <Select
                  value={entityType}
                  onValueChange={(value) => {
                    setValue('entityId', '', { shouldDirty: true });
                    setValue(
                      'entityType',
                      value as TaskFormData['entityType'],
                      {
                        shouldDirty: true,
                        shouldValidate: true,
                      },
                    );
                  }}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select entity type" />
                  </SelectTrigger>

                  <SelectContent>
                    <SelectItem value="LEAD">Lead</SelectItem>
                    <SelectItem value="CONTACT">Contact</SelectItem>
                    <SelectItem value="ACCOUNT">Account</SelectItem>
                    <SelectItem value="DEAL">Deal</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            {entityType && (
              <div className="space-y-2">
                <label className="text-sm font-medium">
                  Linked {entityType.charAt(0) + entityType.slice(1).toLowerCase()}
                </label>

                <RecordCombobox
                  entityType={entityType as 'LEAD' | 'CONTACT' | 'ACCOUNT' | 'DEAL'}
                  value={watch('entityId') || undefined}
                  onChange={(id) =>
                    setValue('entityId', id ?? '', {
                      shouldDirty: true,
                      shouldValidate: true,
                    })
                  }
                  placeholder={`Search and link a ${entityType.toLowerCase()}...`}
                />

                {errors.entityId && (
                  <p className="text-sm text-destructive">
                    {errors.entityId.message}
                  </p>
                )}
              </div>
            )}

            <div className="flex justify-end gap-2 pt-2">
              <Button
                type="button"
                variant="outline"
                disabled={createTask.isPending}
                onClick={() => router.push('/tasks')}
              >
                Cancel
              </Button>

              <Button
                type="submit"
                disabled={createTask.isPending}
              >
                {createTask.isPending
                  ? 'Creating...'
                  : 'Create Task'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}