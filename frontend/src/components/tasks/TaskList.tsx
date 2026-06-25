import React from 'react';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useTasks } from '@/hooks/tasks/useTasks';
import type { TaskListParams } from '@/lib/api/tasks';
import { format } from 'date-fns';
import { Pencil, Trash2, CheckCircle, RotateCcw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { usePermissions } from '@/hooks/usePermissions';

interface TaskListProps {
  entityType?: string;
  entityId?: string;
}

export function TaskList({ entityType, entityId }: TaskListProps) {
  const navigate = useNavigate();
  const { hasPermission } = usePermissions();
  const [params, setParams] = React.useState<TaskListParams>({
    entityType,
    entityId,
    page: 0,
    size: 10,
    sort: 'createdAt,desc',
  });

  const { data, isLoading } = useTasks(params);

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return 'bg-green-500';
      case 'IN_PROGRESS':
        return 'bg-blue-500';
      case 'WAITING':
        return 'bg-yellow-500';
      case 'CANCELLED':
        return 'bg-gray-500';
      default:
        return 'bg-gray-400';
    }
  };

  const getPriorityColor = (priority: string) => {
    switch (priority) {
      case 'URGENT':
        return 'bg-red-500';
      case 'HIGH':
        return 'bg-orange-500';
      case 'MEDIUM':
        return 'bg-yellow-500';
      default:
        return 'bg-blue-500';
    }
  };

  if (isLoading) {
    return <div>Loading...</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div className="flex gap-2">
          <Input
            placeholder="Search tasks..."
            className="w-64"
            onChange={(e) =>
              setParams((prev) => ({ ...prev, search: e.target.value, page: 0 }))
            }
          />
          <Select
            value={params.status}
            onValueChange={(value) =>
              setParams((prev) => ({ ...prev, status: value || undefined, page: 0 }))
            }
          >
            <SelectTrigger className="w-32">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="NOT_STARTED">Not Started</SelectItem>
              <SelectItem value="IN_PROGRESS">In Progress</SelectItem>
              <SelectItem value="WAITING">Waiting</SelectItem>
              <SelectItem value="COMPLETED">Completed</SelectItem>
              <SelectItem value="CANCELLED">Cancelled</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={params.priority}
            onValueChange={(value) =>
              setParams((prev) => ({ ...prev, priority: value || undefined, page: 0 }))
            }
          >
            <SelectTrigger className="w-32">
              <SelectValue placeholder="Priority" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="LOW">Low</SelectItem>
              <SelectItem value="MEDIUM">Medium</SelectItem>
              <SelectItem value="HIGH">High</SelectItem>
              <SelectItem value="URGENT">Urgent</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {hasPermission('task:write') && (
          <Button onClick={() => navigate('/tasks/new')}>New Task</Button>
        )}
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Subject</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Priority</TableHead>
            <TableHead>Due Date</TableHead>
            <TableHead>Entity</TableHead>
            <TableHead>Assigned To</TableHead>
            <TableHead>Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data?.content.map((task) => (
            <TableRow key={task.id}>
              <TableCell className="font-medium">{task.subject}</TableCell>
              <TableCell>
                <Badge className={getStatusColor(task.status)}>{task.status}</Badge>
              </TableCell>
              <TableCell>
                <Badge className={getPriorityColor(task.priority)}>{task.priority}</Badge>
              </TableCell>
              <TableCell>
                {task.dueDate && (
                  <span className={task.isOverdue ? 'text-red-500' : ''}>
                    {format(new Date(task.dueDate), 'MMM dd, yyyy')}
                  </span>
                )}
              </TableCell>
              <TableCell>{task.entityName || '-'}</TableCell>
              <TableCell>{task.assignedTo?.name || '-'}</TableCell>
              <TableCell>
                <div className="flex gap-2">
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => navigate(`/tasks/${task.id}`)}
                  >
                    View
                  </Button>
                  {hasPermission('task:write') && !task.isClosed && (
                    <>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => navigate(`/tasks/${task.id}/edit`)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => {/* complete task */}}
                      >
                        <CheckCircle className="h-4 w-4" />
                      </Button>
                    </>
                  )}
                  {hasPermission('task:write') && task.isClosed && (
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => {/* reopen task */}}
                    >
                      <RotateCcw className="h-4 w-4" />
                    </Button>
                  )}
                  {hasPermission('task:delete') && (
                    <Button variant="ghost" size="icon">
                      <Trash2 className="h-4 w-4 text-red-500" />
                    </Button>
                  )}
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <div className="flex justify-between items-center">
        <div>
          Showing {data?.numberOfElements} of {data?.totalElements} tasks
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            disabled={!data?.first}
            onClick={() => setParams((prev) => ({ ...prev, page: (prev.page || 0) - 1 }))}
          >
            Previous
          </Button>
          <Button
            variant="outline"
            disabled={!data?.last}
            onClick={() => setParams((prev) => ({ ...prev, page: (prev.page || 0) + 1 }))}
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  );
}
