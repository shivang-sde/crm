import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { MeetingForm } from '@/components/meetings/MeetingForm';
import { useNavigate } from 'react-router-dom';

export function NewMeetingPage() {
  const navigate = useNavigate();

  return (
    <div className="container mx-auto py-6">
      <Card>
        <CardHeader>
          <CardTitle>Create New Meeting</CardTitle>
        </CardHeader>
        <CardContent>
          <MeetingForm
            onSuccess={() => navigate('/meetings')}
            onCancel={() => navigate('/meetings')}
          />
        </CardContent>
      </Card>
    </div>
  );
}
