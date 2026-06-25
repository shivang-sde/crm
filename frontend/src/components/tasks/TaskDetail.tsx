import React from 'react';
import { useTask, useCompleteTask, useReopenTask, useDeleteTask } from '@/hooks/tasks/useTasks';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { format } from 'date-fns';
import { useParams, useNavigate } from 'react-router-dom';
import { Pencil, Trash2, CheckCircle, RotateCcw, ArrowLeft } from 'lucide-react';
import { usePermissions } from '@/hooks/usePermissions';

export function TaskDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { hasPermission } = usePermissions();
  const { data: task, isLoading } = useTask(id!);
  const completeMutation = useCompleteTask();
  const reopenMutation = useReopenTask();
  const deleteMutation = useDeleteTask();

  if (isLoading) {
    return <div>Loading...</div>;
  }

  if (!task) {
    return <div>Task not found</div>;
  }

  const handleComplete = () => {
    completeMutation.mutate(id!, {
      onSuccess: () => navigate(`/tasks/${id}`),
    });
  };

  const handleReopen = () => {
    reopenMutation.mutate(id!, {
      onSuccess: () => navigate(`/tasks/${id}`),
    });
  };

  const handleDelete = () => {
    if (confirm('Are you sure you want to delete this task?')) {
      deleteMutation.mutate(id!, {
        onSuccess: () => navigate('/tasks'),
      });
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <Button variant="ghost" onClick={() => navigate('/tasks')}>
          <ArrowLeft className="mr-2 h-4 w-4" /> Back to Tasks
        </Button>
        <div className="flex gap-2">
          {hasPermission('task:write') && !task.isClosed && (
            <>
              <Button onClick={() => navigate(`/tasks/${id}/edit`)}>
                <Pencil className="mr-2 h-4 w-4" /> Edit
              </Button>
              <Button onClick={handleComplete}>
                <CheckCircle className="mr-2 h-4 w-4" /> Complete
              </Button>
            </>
          )}
          {hasPermission('task:write') && task.isClosed && (
            <Button onClick={handleReopen}>
              <RotateCcw className="mr-2 h-4 w-4" /> Reopen
            </Button>
          )}
          {hasPermission('task:delete') && (
            <Button variant="destructive" onClick={handleDelete}>
              <Trash2 className="mr-2 h-4 w-4" /> Delete
            </Button>
          )}
        </div>
      </div>

      <Card>
        <CardHeader>
          <div className="flex justify-between items-start">
            <div>
              <CardTitle className="text-2xl">{task.subject}</CardTitle>
              <div className="flex gap-2 mt-2">
                <Badge>{task.status}</Badge>
                <Badge variant={task.priority === 'URGENT' ? 'destructive' : 'secondary'}>
                  {task.priority}
                </Badge>
                {task.isOverdue && <Badge variant="destructive">Overdue</Badge>}
              </div>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {task.description && (
            <div>
              <h3 className="font-semibold mb-2">Description</h3>
              <p className="text-muted-foreground">{task.description}</p>
            </div>
          )}

          <div className="grid grid-cols-2 gap-4">
            <div>
              <h3 className="font-semibold">Due Date</h3>
              <p>{task.dueDate ? format(new Date(task.dueDate), 'MMM dd, yyyy') : 'Not set'}</p>
            </div>
            <div>
              <h3 className="font-semibold">Completed At</h3>
              <p>{task.completedAt ? format(new Date(task.completedAt), 'MMM dd, yyyy HH:mm') : '-'}</p>
            </div>
          </div>

          {task.entityName && (
            <div>
              <h3 className="font-semibold">Related Entity</h3>
              <p>{task.entityName} ({task.entityType})</p>
            </div>
          )}

          {task.assignedTo && (
            <div>
              <h3 className="font-semibold">Assigned To</h3>
              <p>{task.assignedTo.name} ({task.assignedTo.email})</p>
            </div>
          )}

          {task.remindAt && (
            <div>
              <h3 className="font-semibold">Reminder</h3>
              <p>{format(new Date(task.remindAt), 'MMM dd, yyyy HH:mm')}</p>
            </div>
          )}

          {task.recurrence && (
            <div>
              <h3 className="font-semibold">Recurrence</h3>
              <p>{task.recurrence.repeatType}</p>
            </div>
          )}

          <div className="border-t pt-4">
            <h3 className="font-semibold">Audit Information</h3>
            <div className="grid grid-cols-2 gap-4 mt-2 text-sm text-muted-foreground">
              <div>Created: {format(new Date(task.createdAt), 'MMM dd, yyyy HH:mm')} by {task.createdBy.name}</div>
              <div>Updated: {format(new Date(task.updatedAt), 'MMM dd, yyyy HH:mm')}</div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
