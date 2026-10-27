'use client';

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Spinner } from '@/components/ui/spinner';
import { Eye, Pencil } from 'lucide-react';
import { useRouter } from 'next/navigation';

import { useUserLookup } from '@/lib/hooks/useUserLookup';
import { formatDateTime } from '@/lib/utils';
import { TicketSlaBadge } from './TicketSla';
import { TicketStatusBadge } from './TicketStatusControl';
import type { TicketListMeta, TicketResponse, TicketStatus } from '@/types/tickets';

interface TicketTableProps {
  data: TicketResponse[];
  isLoading: boolean;
  meta?: TicketListMeta;
  filters: { page: number; size: number; search: string; status: TicketStatus | '' };
  onFiltersChange: (filters: TicketTableProps['filters']) => void;
  canEdit: boolean;
}

export default function TicketTable({
  data,
  isLoading,
  meta,
  filters,
  onFiltersChange,
  canEdit,
}: TicketTableProps) {
  const router = useRouter();
  const { resolveUserName } = useUserLookup();

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner />
      </div>
    );
  }

  if (!data.length) {
    return (
      <div className="rounded-md border p-8 text-center text-sm text-muted-foreground">
        No tickets found.
      </div>
    );
  }

  const totalPages = meta?.totalPages ?? 1;

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        <Input
          placeholder="Search subject"
          value={filters.search}
          onChange={(e) => onFiltersChange({ ...filters, search: e.target.value, page: 0 })}
          className="max-w-xs"
        />
        <Select
          value={filters.status || 'ALL'}
          onValueChange={(v) =>
            onFiltersChange({
              ...filters,
              status: v === 'ALL' ? '' : (v as TicketStatus),
              page: 0,
            })
          }
        >
          <SelectTrigger className="w-48">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All statuses</SelectItem>
            <SelectItem value="OPEN">Open</SelectItem>
            <SelectItem value="IN_PROGRESS">In progress</SelectItem>
            <SelectItem value="RESOLVED">Resolved</SelectItem>
            <SelectItem value="CLOSED">Closed</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Subject</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>SLA</TableHead>
              <TableHead>Owner</TableHead>
              <TableHead>Updated</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((ticket) => (
              <TableRow key={ticket.id}>
                <TableCell className="font-medium">
                  <button
                    type="button"
                    className="text-left hover:underline"
                    onClick={() => router.push(`/tickets/${ticket.id}`)}
                  >
                    {ticket.subject}
                  </button>
                  <p className="text-xs text-muted-foreground">
                    {formatDateTime(ticket.createdAt)}
                  </p>
                </TableCell>
                <TableCell>
                  <TicketStatusBadge status={ticket.status} />
                </TableCell>
                <TableCell>
                  <TicketSlaBadge ticket={ticket} />
                </TableCell>
                <TableCell>{resolveUserName(ticket.ownerId)}</TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {formatDateTime(ticket.updatedAt)}
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end gap-1">
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label="View ticket"
                      onClick={() => router.push(`/tickets/${ticket.id}`)}
                    >
                      <Eye className="h-4 w-4" />
                    </Button>
                    {canEdit && (
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label="Edit ticket"
                        onClick={() => router.push(`/tickets/${ticket.id}/edit`)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <Button
            variant="outline"
            size="sm"
            disabled={filters.page === 0}
            onClick={() => onFiltersChange({ ...filters, page: Math.max(0, filters.page - 1) })}
          >
            Previous
          </Button>
          <span className="text-muted-foreground">
            Page {filters.page + 1} of {totalPages} ({meta?.total ?? data.length} tickets)
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={filters.page + 1 >= totalPages}
            onClick={() => onFiltersChange({ ...filters, page: filters.page + 1 })}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  );
}
