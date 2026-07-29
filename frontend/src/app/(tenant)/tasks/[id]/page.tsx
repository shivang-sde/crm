'use client';

import { useRouter, useParams } from 'next/navigation';
import { useTask } from '@/lib/hooks/tasks';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Spinner } from '@/components/ui/spinner';
import { ArrowLeft, Pencil, CheckCircle, RotateCcw, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { useCompleteTask, useReopenTask, useDeleteTask } from '@/lib/hooks/tasks';

export default function TaskDetailPage() {
  const router = useRouter();
  const params = useParams<{ id?: string | string[] }>();
  const rawId = params?.id;
  const id = typeof rawId === 'string' ? rawId : rawId?.[0] ?? '';
  const { canEditTasks, canDeleteTasks } = usePermissions();
  const { data: task, isLoading } = useTask(id);
  const completeTask = useCompleteTask();
  const reopenTask = useReopenTask();
  const deleteTask = useDeleteTask();


console.log('TaskDetailPage data:', task);  

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner />
      </div>
    );
  }

  if (!task) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">Task not found</p>
      </div>
    );
  }

  const handleComplete = async () => {
    try {
      await completeTask.mutateAsync(id);
      toast.success('Task marked as complete');
    } catch (error) {
      toast.error('Failed to complete task');
    }
  };

  const handleReopen = async () => {
    try {
      await reopenTask.mutateAsync(id);
      toast.success('Task reopened');
    } catch (error) {
      toast.error('Failed to reopen task');
    }
  };

  const handleDelete = async () => {
    if (!confirm('Are you sure you want to delete this task?')) return;
    try {
      await deleteTask.mutateAsync(id);
      toast.success('Task deleted successfully');
      router.push('/tasks');
    } catch (error) {
      toast.error('Failed to delete task');
    }
  };

  const statusColors: Record<string, string> = {
    NOT_STARTED: 'bg-gray-100 text-gray-800',
    IN_PROGRESS: 'bg-blue-100 text-blue-800',
    WAITING: 'bg-yellow-100 text-yellow-800',
    COMPLETED: 'bg-green-100 text-green-800',
    CANCELLED: 'bg-red-100 text-red-800',
  };

  const priorityColors: Record<string, string> = {
    LOW: 'bg-gray-100 text-gray-800',
    MEDIUM: 'bg-blue-100 text-blue-800',
    HIGH: 'bg-orange-100 text-orange-800',
    URGENT: 'bg-red-100 text-red-800',
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="outline" size="icon" onClick={() => router.push('/tasks')}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <h1 className="text-3xl font-bold tracking-tight">{task.subject}</h1>
        </div>
        <div className="flex items-center gap-2">
          {!task.isClosed ? (
            <Button onClick={handleComplete} disabled={completeTask.isPending}>
              <CheckCircle className="mr-2 h-4 w-4" />
              Complete
            </Button>
          ) : (
            <Button variant="outline" onClick={handleReopen} disabled={reopenTask.isPending}>
              <RotateCcw className="mr-2 h-4 w-4" />
              Reopen
            </Button>
          )}
          {canEditTasks && (
            <Button variant="outline" onClick={() => router.push(`/tasks/${id}/edit`)}>
              <Pencil className="mr-2 h-4 w-4" />
              Edit
            </Button>
          )}
          {canDeleteTasks && (
            <Button variant="destructive" onClick={handleDelete} disabled={deleteTask.isPending}>
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </Button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Task Details</CardTitle>
            <CardDescription>Basic information about the task</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Status</p>
              <Badge className={statusColors[task.status]}>
                {task.status.replace('_', ' ')}
              </Badge>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Priority</p>
              <Badge className={priorityColors[task.priority]}>
                {task.priority}
              </Badge>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Due Date</p>
              <p className="text-sm">
                {task.dueDate ? new Date(task.dueDate).toLocaleString() : 'Not set'}
                {task.isOverdue && !task.isClosed && (
                  <span className="text-red-500 ml-2">(Overdue)</span>
                )}
              </p>
            </div>
            {task.remindAt && (
              <div>
                <p className="text-sm font-medium text-muted-foreground">Reminder</p>
                <p className="text-sm">{new Date(task.remindAt).toLocaleString()}</p>
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Assignment</CardTitle>
            <CardDescription>Task ownership and linking</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              {/* <p className="text-sm font-medium text-muted-foreground">Assigned To</p>
              <p className="text-sm">
                {task.assignedTo?.name || task.createdBy?.name || 'Unassigned'}
              </p> */}
              {task.assignedTo?.email && (
                <p className="text-xs text-muted-foreground">{task.assignedTo.email}</p>
              )}
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Linked Entity</p>
              <p className="text-sm">
                {task.entityName || 'Not linked'}
                {task.entityType && !task.entityName && (
                  <span className="text-xs text-muted-foreground"> ({task.entityType})</span>
                )}
              </p>
            </div>
            {/* <div>
              <p className="text-sm font-medium text-muted-foreground">Created By</p>
              <p className="text-sm">{task.createdBy.name ?? 'N/A'}</p>
              <p className="text-xs text-muted-foreground">{task.createdBy.email}</p>
            </div> */}
          </CardContent>
        </Card>
      </div>

      {task.description && (
        <Card>
          <CardHeader>
            <CardTitle>Description</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm whitespace-pre-wrap">{task.description}</p>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Timestamps</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-4">
          <div>
            <p className="text-sm font-medium text-muted-foreground">Created At</p>
            <p className="text-sm">{new Date(task.createdAt).toLocaleString()}</p>
          </div>
          <div>
            <p className="text-sm font-medium text-muted-foreground">Updated At</p>
            <p className="text-sm">{new Date(task.updatedAt).toLocaleString()}</p>
          </div>
          {task.completedAt && (
            <div>
              <p className="text-sm font-medium text-muted-foreground">Completed At</p>
              <p className="text-sm">{new Date(task.completedAt).toLocaleString()}</p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
