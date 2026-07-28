'use client';

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
import { Spinner } from '@/components/ui/spinner';
import { TaskResponse, TaskStatus, TaskPriority } from '@/types/tasks';
import { Eye, Pencil, Trash2 } from 'lucide-react';

interface TaskTableProps {
  data: TaskResponse[];
  isLoading: boolean;
  onView: (task: TaskResponse) => void;
  onEdit?: (task: TaskResponse) => void;
  onDelete?: (task: TaskResponse) => void;
  filters: {
    page: number;
    size: number;
    sort: string;
    search: string;
    status: string;
    priority: string;
  };
  onFiltersChange: (filters: any) => void;
  totalElements: number;
  totalPages: number;
}

const statusColors: Record<TaskStatus, string> = {
  NOT_STARTED: 'bg-gray-100 text-gray-800',
  IN_PROGRESS: 'bg-blue-100 text-blue-800',
  WAITING_ON_SOMEONE: 'bg-yellow-100 text-yellow-800',
  DEFERRED: 'bg-purple-100 text-purple-800',
  COMPLETED: 'bg-green-100 text-green-800',
};


const priorityColors: Record<TaskPriority, string> = {
  LOW: 'bg-gray-100 text-gray-800',
  MEDIUM: 'bg-blue-100 text-blue-800',
  HIGH: 'bg-orange-100 text-orange-800',
  URGENT: 'bg-red-100 text-red-800',
};


export default function TaskTable({
  data,
  isLoading,
  onView,
  onEdit,
  onDelete,
  filters,
  onFiltersChange,
  totalElements,
  totalPages,
}: TaskTableProps) {
  const handlePageChange = (newPage: number) => {
    onFiltersChange({ ...filters, page: newPage });
  };

  const handleSearchChange = (value: string) => {
    onFiltersChange({ ...filters, search: value, page: 0 });
  };

  const handleStatusChange = (value: string) => {
    onFiltersChange({ ...filters, status: value, page: 0 });
  };

  const handlePriorityChange = (value: string) => {
    onFiltersChange({ ...filters, priority: value, page: 0 });
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-4">
        <Input
          placeholder="Search tasks..."
          value={filters.search}
          onChange={(e) => handleSearchChange(e.target.value)}
          className="max-w-sm"
        />
        <Select value={filters.status} onValueChange={handleStatusChange}>
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="All Statuses" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="">All Statuses</SelectItem>
            <SelectItem value="NOT_STARTED">Not Started</SelectItem>
            <SelectItem value="IN_PROGRESS">In Progress</SelectItem>
            <SelectItem value="WAITING">Waiting</SelectItem>
            <SelectItem value="COMPLETED">Completed</SelectItem>
            <SelectItem value="CANCELLED">Cancelled</SelectItem>
          </SelectContent>
        </Select>
        <Select value={filters.priority} onValueChange={handlePriorityChange}>
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="All Priorities" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="">All Priorities</SelectItem>
            <SelectItem value="LOW">Low</SelectItem>
            <SelectItem value="MEDIUM">Medium</SelectItem>
            <SelectItem value="HIGH">High</SelectItem>
            <SelectItem value="URGENT">Urgent</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Subject</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Priority</TableHead>
              <TableHead>Due Date</TableHead>
              <TableHead>Entity</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                  No tasks found
                </TableCell>
              </TableRow>
            ) : (
              data.map((task) => (
                <TableRow key={task.id}>
                  <TableCell className="font-medium">{task.subject}</TableCell>
                  <TableCell>
                    <Badge className={statusColors[task.status]}>
                      {task.status.replace('_', ' ')}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Badge className={priorityColors[task.priority]}>
                      {task.priority}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {task.dueDate ? new Date(task.dueDate).toLocaleDateString() : '-'}
                    {task.isOverdue && !task.isClosed && (
                      <span className="text-red-500 text-xs ml-1">(Overdue)</span>
                    )}
                  </TableCell>
                  <TableCell>
                    {task.entityName ? (
                      <Badge variant="outline">{task.entityName}</Badge>
                    ) : (
                      '-'
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-2">
                      <Button variant="ghost" size="icon" onClick={() => onView(task)}>
                        <Eye className="h-4 w-4" />
                      </Button>
                      {onEdit && (
                        <Button variant="ghost" size="icon" onClick={() => onEdit(task)}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                      )}
                      {onDelete && (
                        <Button variant="ghost" size="icon" onClick={() => onDelete(task)}>
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          Showing {data.length} of {totalElements} tasks
        </p>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={filters.page === 0}
            onClick={() => handlePageChange(filters.page - 1)}
          >
            Previous
          </Button>
          <span className="text-sm">
            Page {filters.page + 1} of {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={filters.page >= totalPages - 1}
            onClick={() => handlePageChange(filters.page + 1)}
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  );
}
