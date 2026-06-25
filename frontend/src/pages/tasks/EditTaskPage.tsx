import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { TaskForm } from '@/components/tasks/TaskForm';
import { useTask } from '@/hooks/tasks/useTasks';
import { useParams, useNavigate } from 'react-router-dom';

export function EditTaskPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: task, isLoading } = useTask(id!);

  if (isLoading) {
    return <div>Loading...</div>;
  }

  if (!task) {
    return <div>Task not found</div>;
  }

  return (
    <div className="container mx-auto py-6">
      <Card>
        <CardHeader>
          <CardTitle>Edit Task</CardTitle>
        </CardHeader>
        <CardContent>
          <TaskForm
            task={task}
            onSuccess={() => navigate(`/tasks/${id}`)}
            onCancel={() => navigate(`/tasks/${id}`)}
          />
        </CardContent>
      </Card>
    </div>
  );
}
