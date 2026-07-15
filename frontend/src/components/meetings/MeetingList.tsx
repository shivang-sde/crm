import React from 'react';
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
import { useMeetings } from '@/hooks/tasks/useMeetings';
import type { MeetingListParams } from '@/lib/api/meetings';
import type { MeetingResponse } from '@/types/meetings';
import { format } from 'date-fns';
import { Pencil, Trash2, Users } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { usePermissions } from '@/hooks/usePermissions';

interface MeetingListProps {
  entityType?: string;
  entityId?: string;
}

export function MeetingList({ entityType, entityId }: MeetingListProps) {
  const navigate = useNavigate();
  const { hasPermission } = usePermissions();
  const [params, setParams] = React.useState<MeetingListParams>({
    entityType,
    entityId,
    page: 0,
    size: 10,
    sort: 'createdAt,desc',
  });

  const { data, isLoading } = useMeetings(params);

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'HELD':
        return 'bg-green-500';
      case 'PLANNED':
        return 'bg-blue-500';
      case 'NOT_HELD':
        return 'bg-yellow-500';
      case 'CANCELLED':
        return 'bg-gray-500';
      default:
        return 'bg-gray-400';
    }
  };

  if (isLoading) {
    return <div>Loading...</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div className="flex gap-2">
          <Input
            placeholder="Search meetings..."
            className="w-64"
            onChange={(e) =>
              setParams((prev) => ({ ...prev, search: e.target.value, page: 0 }))
            }
          />
          <Select
            value={params.status}
            onValueChange={(value) =>
              setParams((prev) => ({ ...prev, status: value || undefined, page: 0 }))
            }
          >
            <SelectTrigger className="w-32">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="PLANNED">Planned</SelectItem>
              <SelectItem value="HELD">Held</SelectItem>
              <SelectItem value="NOT_HELD">Not Held</SelectItem>
              <SelectItem value="CANCELLED">Cancelled</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {hasPermission('meeting:write') && (
          <Button onClick={() => navigate('/meetings/new')}>New Meeting</Button>
        )}
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Subject</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Location</TableHead>
            <TableHead>Start Time</TableHead>
            <TableHead>End Time</TableHead>
            <TableHead>Attendees</TableHead>
            <TableHead>Entity</TableHead>
            <TableHead>Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data?.content.map((meeting: MeetingResponse) => (
            <TableRow key={meeting.id}>
              <TableCell className="font-medium">{meeting.subject}</TableCell>
              <TableCell>
                <Badge className={getStatusColor(meeting.status)}>{meeting.status}</Badge>
              </TableCell>
              <TableCell>{meeting.location || '-'}</TableCell>
              <TableCell>
                {format(new Date(meeting.startTime), 'MMM dd, yyyy HH:mm')}
              </TableCell>
              <TableCell>
                {format(new Date(meeting.endTime), 'MMM dd, yyyy HH:mm')}
              </TableCell>
              <TableCell>
                <div className="flex items-center gap-1">
                  <Users className="h-4 w-4" />
                  {meeting.attendees?.length || 0}
                </div>
              </TableCell>
              <TableCell>{meeting.entityName || '-'}</TableCell>
              <TableCell>
                <div className="flex gap-2">
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => navigate(`/meetings/${meeting.id}`)}
                  >
                    View
                  </Button>
                  {hasPermission('meeting:write') && (
                    <>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => navigate(`/meetings/${meeting.id}/edit`)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      {hasPermission('meeting:delete') && (
                        <Button variant="ghost" size="icon">
                          <Trash2 className="h-4 w-4 text-red-500" />
                        </Button>
                      )}
                    </>
                  )}
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <div className="flex justify-between items-center">
        <div>
          Showing {data?.numberOfElements} of {data?.totalElements} meetings
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            disabled={!data?.first}
            onClick={() => setParams((prev) => ({ ...prev, page: (prev.page || 0) - 1 }))}
          >
            Previous
          </Button>
          <Button
            variant="outline"
            disabled={!data?.last}
            onClick={() => setParams((prev) => ({ ...prev, page: (prev.page || 0) + 1 }))}
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  );
}
