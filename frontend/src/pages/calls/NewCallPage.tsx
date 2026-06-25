import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { CallForm } from '@/components/calls/CallForm';
import { useNavigate } from 'react-router-dom';

export function NewCallPage() {
  const navigate = useNavigate();

  return (
    <div className="container mx-auto py-6">
      <Card>
        <CardHeader>
          <CardTitle>Create New Call</CardTitle>
        </CardHeader>
        <CardContent>
          <CallForm
            onSuccess={() => navigate('/calls')}
            onCancel={() => navigate('/calls')}
          />
        </CardContent>
      </Card>
    </div>
  );
}
