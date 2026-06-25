import React from 'react';
import { TaskList } from '@/components/tasks/TaskList';
import { Button } from '@/components/ui/button';
import { useNavigate } from 'react-router-dom';
import { usePermissions } from '@/hooks/usePermissions';
import { Plus } from 'lucide-react';

export function TasksPage() {
  const navigate = useNavigate();
  const { hasPermission } = usePermissions();

  return (
    <div className="container mx-auto py-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">Tasks</h1>
          <p className="text-muted-foreground">Manage your tasks and to-dos</p>
        </div>
        {hasPermission('task:write') && (
          <Button onClick={() => navigate('/tasks/new')}>
            <Plus className="mr-2 h-4 w-4" /> New Task
          </Button>
        )}
      </div>
      <TaskList />
    </div>
  );
}
