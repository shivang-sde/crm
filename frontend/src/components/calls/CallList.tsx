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
import { useCalls } from '@/hooks/tasks/useCalls';
import type { CallListParams } from '@/lib/api/calls';
import { format } from 'date-fns';
import { Pencil, Trash2, Phone } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { usePermissions } from '@/hooks/usePermissions';

interface CallListProps {
  entityType?: string;
  entityId?: string;
}

export function CallList({ entityType, entityId }: CallListProps) {
  const navigate = useNavigate();
  const { hasPermission } = usePermissions();
  const [params, setParams] = React.useState<CallListParams>({
    entityType,
    entityId,
    page: 0,
    size: 10,
    sort: 'createdAt,desc',
  });

  const { data, isLoading } = useCalls(params);

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

  const getCallTypeColor = (type: string) => {
    return type === 'INCOMING' ? 'bg-green-600' : 'bg-blue-600';
  };

  if (isLoading) {
    return <div>Loading...</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div className="flex gap-2">
          <Input
            placeholder="Search calls..."
            className="w-64"
            onChange={(e) =>
              setParams((prev) => ({ ...prev, search: e.target.value, page: 0 }))
            }
          />
          <Select
            value={params.callType}
            onValueChange={(value) =>
              setParams((prev) => ({ ...prev, callType: value || undefined, page: 0 }))
            }
          >
            <SelectTrigger className="w-32">
              <SelectValue placeholder="Type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="INCOMING">Incoming</SelectItem>
              <SelectItem value="OUTGOING">Outgoing</SelectItem>
            </SelectContent>
          </Select>
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
        {hasPermission('call:write') && (
          <Button onClick={() => navigate('/calls/new')}>New Call</Button>
        )}
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Subject</TableHead>
            <TableHead>Type</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Phone</TableHead>
            <TableHead>Duration</TableHead>
            <TableHead>Date</TableHead>
            <TableHead>Entity</TableHead>
            <TableHead>Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data?.content.map((call) => (
            <TableRow key={call.id}>
              <TableCell className="font-medium">{call.subject}</TableCell>
              <TableCell>
                <Badge className={getCallTypeColor(call.callType)}>{call.callType}</Badge>
              </TableCell>
              <TableCell>
                <Badge className={getStatusColor(call.status)}>{call.status}</Badge>
              </TableCell>
              <TableCell>{call.phoneNumber || '-'}</TableCell>
              <TableCell>
                {call.durationMinutes ? `${call.durationMinutes} min` : '-'}
              </TableCell>
              <TableCell>
                {call.startTime && format(new Date(call.startTime), 'MMM dd, yyyy HH:mm')}
              </TableCell>
              <TableCell>{call.entityName || '-'}</TableCell>
              <TableCell>
                <div className="flex gap-2">
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => navigate(`/calls/${call.id}`)}
                  >
                    View
                  </Button>
                  {hasPermission('call:write') && (
                    <>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => navigate(`/calls/${call.id}/edit`)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      {hasPermission('call:delete') && (
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
          Showing {data?.numberOfElements} of {data?.totalElements} calls
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
