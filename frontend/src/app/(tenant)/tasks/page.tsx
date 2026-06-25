'use client';

import { useRouter } from 'next/navigation';
import { Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useTasks } from '@/lib/hooks/tasks';
import { usePermissions } from '@/lib/hooks/usePermissions';
import TaskTable from './_components/TaskTable';
import { useState } from 'react';
import { TaskResponse } from '@/types/tasks';

export default function TasksPage() {
  const router = useRouter();
  const { canViewTasks, canEditTasks } = usePermissions();
  const [filters, setFilters] = useState({
    page: 0,
    size: 10,
    sort: 'createdAt,desc',
    search: '',
    status: '',
    priority: '',
  });

  const { data, isLoading, error } = useTasks(filters as any);

  if (!canViewTasks) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">You don&apos;t have permission to view tasks.</p>
      </div>
    );
  }

  const handleCreateNew = () => {
    router.push('/tasks/new');
  };

  const handleViewTask = (task: TaskResponse) => {
    router.push(`/tasks/${task.id}`);
  };

  const handleEditTask = (task: TaskResponse) => {
    router.push(`/tasks/${task.id}/edit`);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Tasks</h1>
          <p className="text-muted-foreground">Manage your tasks and to-dos</p>
        </div>
        {canEditTasks && (
          <Button onClick={handleCreateNew}>
            <Plus className="mr-2 h-4 w-4" />
            New Task
          </Button>
        )}
      </div>

      <TaskTable
        data={data?.content || []}
        isLoading={isLoading}
        onView={handleViewTask}
        onEdit={canEditTasks ? handleEditTask : undefined}
        filters={filters}
        onFiltersChange={setFilters}
        totalElements={data?.totalElements || 0}
        totalPages={data?.totalPages || 0}
      />
    </div>
  );
}
