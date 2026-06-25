import React from 'react';
import { MeetingList } from '@/components/meetings/MeetingList';
import { Button } from '@/components/ui/button';
import { useNavigate } from 'react-router-dom';
import { usePermissions } from '@/hooks/usePermissions';
import { Plus } from 'lucide-react';

export function MeetingsPage() {
  const navigate = useNavigate();
  const { hasPermission } = usePermissions();

  return (
    <div className="container mx-auto py-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">Meetings</h1>
          <p className="text-muted-foreground">Schedule and track meetings</p>
        </div>
        {hasPermission('meeting:write') && (
          <Button onClick={() => navigate('/meetings/new')}>
            <Plus className="mr-2 h-4 w-4" /> New Meeting
          </Button>
        )}
      </div>
      <MeetingList />
    </div>
  );
}
