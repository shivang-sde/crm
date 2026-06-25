import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { TaskForm } from '@/components/tasks/TaskForm';
import { useNavigate } from 'react-router-dom';

export function NewTaskPage() {
  const navigate = useNavigate();

  return (
    <div className="container mx-auto py-6">
      <Card>
        <CardHeader>
          <CardTitle>Create New Task</CardTitle>
        </CardHeader>
        <CardContent>
          <TaskForm
            onSuccess={() => navigate('/tasks')}
            onCancel={() => navigate('/tasks')}
          />
        </CardContent>
      </Card>
    </div>
  );
}
