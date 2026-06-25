'use client';

import { useRouter } from 'next/navigation';
import { Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useMeetings } from '@/lib/hooks/meetings';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { useState } from 'react';
import { MeetingResponse } from '@/types/meetings';

export default function MeetingsPage() {
  const router = useRouter();
  const { canViewMeetings, canEditMeetings } = usePermissions();
  const [filters, setFilters] = useState({
    page: 0,
    size: 10,
    sort: 'createdAt,desc',
    search: '',
    status: '',
  });

  const { data, isLoading } = useMeetings(filters as any);

  if (!canViewMeetings) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">You don&apos;t have permission to view meetings.</p>
      </div>
    );
  }

  const handleCreateNew = () => {
    router.push('/meetings/new');
  };

  const handleViewMeeting = (meeting: MeetingResponse) => {
    router.push(`/meetings/${meeting.id}`);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Meetings</h1>
          <p className="text-muted-foreground">Schedule and track your meetings</p>
        </div>
        {canEditMeetings && (
          <Button onClick={handleCreateNew}>
            <Plus className="mr-2 h-4 w-4" />
            New Meeting
          </Button>
        )}
      </div>

      <div className="rounded-md border p-8 text-center">
        <p className="text-muted-foreground">Meeting list component coming soon...</p>
        <p className="text-sm text-muted-foreground mt-2">
          Total meetings: {data?.totalElements || 0}
        </p>
      </div>
    </div>
  );
}
