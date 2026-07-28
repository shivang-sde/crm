'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { CalendarDays, Plus } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';

import MeetingDataTable, {
  MeetingFilters,
} from '@/components/meetings/MeetingDataTable';

import {
  useDeleteMeeting,
  useMeetings,
} from '@/lib/hooks/meetings';
import { usePermissions } from '@/lib/hooks/usePermissions';

import type { MeetingResponse } from '@/types/meetings';

export default function MeetingsPage() {
  const router = useRouter();

  const {
    canViewMeetings,
    canEditMeetings,
    canDeleteMeetings,
  } = usePermissions();

  const [filters, setFilters] = useState<MeetingFilters>({
    page: 0,
    size: 10,
    sort: 'createdAt,desc',
    search: '',
    status: '',
    meetingType: '',
  });

  const { data, isLoading } = useMeetings(filters);
  const deleteMeeting = useDeleteMeeting();

  if (!canViewMeetings) {
    return (
      <div className="flex h-64 items-center justify-center">
        <p className="text-muted-foreground">
          You don&apos;t have permission to view meetings.
        </p>
      </div>
    );
  }

  const meetings = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 0;

  const handleCreateNew = () => {
    router.push('/meetings/new');
  };

  const handleViewMeeting = (meeting: MeetingResponse) => {
    router.push(`/meetings/${meeting.id}`);
  };

  const handleEditMeeting = (meeting: MeetingResponse) => {
    router.push(`/meetings/${meeting.id}/edit`);
  };

  const handleDeleteMeeting = async (
    meeting: MeetingResponse,
  ) => {
    const confirmed = window.confirm(
      `Are you sure you want to delete "${meeting.subject}"?`,
    );

    if (!confirmed) {
      return;
    }

    try {
      await deleteMeeting.mutateAsync(meeting.id);
      toast.success('Meeting deleted successfully');
    } catch (error) {
      console.error('Failed to delete meeting:', error);
      toast.error('Failed to delete meeting');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <CalendarDays className="h-7 w-7 text-muted-foreground" />

            <h1 className="text-3xl font-bold tracking-tight">
              Meetings
            </h1>
          </div>

          <p className="mt-1 text-muted-foreground">
            Schedule, manage, and track your meetings.
          </p>
        </div>

        {canEditMeetings && (
          <Button onClick={handleCreateNew}>
            <Plus className="mr-2 h-4 w-4" />
            New Meeting
          </Button>
        )}
      </div>

      <MeetingDataTable
        data={meetings}
        isLoading={isLoading}
        filters={filters}
        onFiltersChange={setFilters}
        totalElements={totalElements}
        totalPages={totalPages}
        canEdit={canEditMeetings}
        canDelete={canDeleteMeetings}
        onView={handleViewMeeting}
        onEdit={handleEditMeeting}
        onDelete={handleDeleteMeeting}
      />
    </div>
  );
}