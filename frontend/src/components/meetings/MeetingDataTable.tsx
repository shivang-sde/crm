'use client';

import { useMemo } from 'react';
import {
  ColumnDef,
  SortingState,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import {
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Eye,
  MoreHorizontal,
  Pencil,
  Search,
  Trash2,
  Users,
  Video,
  MapPin,
  Phone,
} from 'lucide-react';

import { MeetingResponse, MeetingStatus, MeetingType } from '@/types/meetings';

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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Spinner } from '@/components/ui/spinner';

const ALL_FILTER_VALUE = 'ALL';

export interface MeetingFilters {
  page: number;
  size: number;
  sort: string;
  search: string;
  status: string;
  meetingType: string;
}

interface MeetingDataTableProps {
  data: MeetingResponse[];
  isLoading: boolean;

  filters: MeetingFilters;
  onFiltersChange: (filters: MeetingFilters) => void;

  totalElements: number;
  totalPages: number;

  canEdit?: boolean;
  canDelete?: boolean;

  onView: (meeting: MeetingResponse) => void;
  onEdit?: (meeting: MeetingResponse) => void;
  onDelete?: (meeting: MeetingResponse) => void;
}

function formatEnumLabel(value?: string | null): string {
  if (!value) {
    return 'Not set';
  }

  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

function formatDateTime(value?: string | null): string {
  if (!value) {
    return 'Not set';
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return 'Invalid date';
  }

  return date.toLocaleString();
}

function getStatusBadgeClass(status: MeetingStatus): string {
  const statusClasses: Record<MeetingStatus, string> = {
    PLANNED:
      'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-900 dark:bg-blue-950 dark:text-blue-300',
    HELD:
      'border-green-200 bg-green-50 text-green-700 dark:border-green-900 dark:bg-green-950 dark:text-green-300',
    NOT_HELD:
      'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-300',
    CANCELLED:
      'border-red-200 bg-red-50 text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-300',
  };

  return statusClasses[status];
}

function MeetingTypeBadge({ type }: { type?: MeetingType }) {
  if (!type) {
    return <span className="text-sm text-muted-foreground">Not set</span>;
  }

  const iconClassName = 'mr-1.5 h-3.5 w-3.5';

  return (
    <Badge variant="outline" className="whitespace-nowrap font-normal">
      {type === 'VIDEO' && <Video className={iconClassName} />}
      {type === 'PHONE' && <Phone className={iconClassName} />}
      {type === 'IN_PERSON' && <MapPin className={iconClassName} />}
      {formatEnumLabel(type)}
    </Badge>
  );
}

function SortableHeader({
  label,
  columnId,
  filters,
  onFiltersChange,
}: {
  label: string;
  columnId: string;
  filters: MeetingFilters;
  onFiltersChange: (filters: MeetingFilters) => void;
}) {
  const [currentColumn, currentDirection] = filters.sort.split(',');
  const isActive = currentColumn === columnId;
  const direction = isActive ? currentDirection : undefined;

  const handleSort = () => {
    const nextDirection =
      isActive && currentDirection === 'asc' ? 'desc' : 'asc';

    onFiltersChange({
      ...filters,
      sort: `${columnId},${nextDirection}`,
      page: 0,
    });
  };

  return (
    <Button
      type="button"
      variant="ghost"
      className="-ml-3 h-8 px-3"
      onClick={handleSort}
    >
      {label}

      {!isActive && <ArrowUpDown className="ml-2 h-4 w-4" />}
      {direction === 'asc' && <ArrowUp className="ml-2 h-4 w-4" />}
      {direction === 'desc' && <ArrowDown className="ml-2 h-4 w-4" />}
    </Button>
  );
}

export default function MeetingDataTable({
  data,
  isLoading,
  filters,
  onFiltersChange,
  totalElements,
  totalPages,
  canEdit = false,
  canDelete = false,
  onView,
  onEdit,
  onDelete,
}: MeetingDataTableProps) {
  const columns = useMemo<ColumnDef<MeetingResponse>[]>(
    () => [
      {
        accessorKey: 'subject',
        header: () => (
          <SortableHeader
            label="Meeting"
            columnId="subject"
            filters={filters}
            onFiltersChange={onFiltersChange}
          />
        ),
        cell: ({ row }) => {
          const meeting = row.original;

          return (
            <button
              type="button"
              className="max-w-[280px] text-left"
              onClick={() => onView(meeting)}
            >
              <p className="truncate font-medium hover:underline">
                {meeting.subject}
              </p>

              {meeting.description && (
                <p className="mt-1 line-clamp-1 text-xs text-muted-foreground">
                  {meeting.description}
                </p>
              )}
            </button>
          );
        },
      },
      {
        accessorKey: 'status',
        header: 'Status',
        cell: ({ row }) => (
          <Badge
            variant="outline"
            className={getStatusBadgeClass(row.original.status)}
          >
            {formatEnumLabel(row.original.status)}
          </Badge>
        ),
      },
      {
        accessorKey: 'meetingType',
        header: 'Type',
        cell: ({ row }) => (
          <MeetingTypeBadge type={row.original.meetingType} />
        ),
      },
      {
        accessorKey: 'startTime',
        header: () => (
          <SortableHeader
            label="Schedule"
            columnId="startTime"
            filters={filters}
            onFiltersChange={onFiltersChange}
          />
        ),
        cell: ({ row }) => {
          const meeting = row.original;

          return (
            <div className="min-w-[190px]">
              <div className="flex items-center gap-2">
                <CalendarDays className="h-4 w-4 text-muted-foreground" />

                <div>
                  <p className="text-sm font-medium">
                    {formatDateTime(meeting.startTime)}
                  </p>

                  {meeting.endTime && (
                    <p className="text-xs text-muted-foreground">
                      Ends {formatDateTime(meeting.endTime)}
                    </p>
                  )}
                </div>
              </div>
            </div>
          );
        },
      },
      {
        accessorKey: 'location',
        header: 'Location',
        cell: ({ row }) => {
          const location = row.original.location;

          if (!location) {
            return <span className="text-muted-foreground">Not set</span>;
          }

          const isLink =
            location.startsWith('http://') || location.startsWith('https://');

          return (
            <div className="max-w-[220px]">
              {isLink ? (
                <a
                  href={location}
                  target="_blank"
                  rel="noreferrer"
                  className="block truncate text-sm text-primary hover:underline"
                  onClick={(event) => event.stopPropagation()}
                >
                  {location}
                </a>
              ) : (
                <span className="block truncate text-sm">{location}</span>
              )}
            </div>
          );
        },
      },
      {
        accessorKey: 'entityName',
        header: 'Meeting For',
        cell: ({ row }) => {
          const meeting = row.original;

          if (!meeting.entityName && !meeting.entityType) {
            return <span className="text-muted-foreground">Not linked</span>;
          }

          return (
            <div>
              <p className="max-w-[180px] truncate text-sm font-medium">
                {meeting.entityName || 'Linked record'}
              </p>

              {meeting.entityType && (
                <p className="text-xs text-muted-foreground">
                  {formatEnumLabel(meeting.entityType)}
                </p>
              )}
            </div>
          );
        },
      },
      {
        accessorKey: 'attendees',
        header: 'Attendees',
        cell: ({ row }) => {
          const count = row.original.attendees?.length ?? 0;

          return (
            <div className="flex items-center gap-2 text-sm">
              <Users className="h-4 w-4 text-muted-foreground" />
              {count}
            </div>
          );
        },
      },
      {
        id: 'actions',
        enableSorting: false,
        header: () => <div className="text-right">Actions</div>,
        cell: ({ row }) => {
          const meeting = row.original;

          return (
            <div className="flex justify-end">
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={`Actions for ${meeting.subject}`}
                  >
                    <MoreHorizontal className="h-4 w-4" />
                  </Button>
                </DropdownMenuTrigger>

                <DropdownMenuContent align="end" className="w-40">
                  <DropdownMenuItem onClick={() => onView(meeting)}>
                    <Eye className="mr-2 h-4 w-4" />
                    View
                  </DropdownMenuItem>

                  {canEdit && onEdit && (
                    <DropdownMenuItem onClick={() => onEdit(meeting)}>
                      <Pencil className="mr-2 h-4 w-4" />
                      Edit
                    </DropdownMenuItem>
                  )}

                  {canDelete && onDelete && (
                    <>
                      <DropdownMenuSeparator />

                      <DropdownMenuItem
                        className="text-destructive focus:text-destructive"
                        onClick={() => onDelete(meeting)}
                      >
                        <Trash2 className="mr-2 h-4 w-4" />
                        Delete
                      </DropdownMenuItem>
                    </>
                  )}
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          );
        },
      },
    ],
    [
      canDelete,
      canEdit,
      filters,
      onDelete,
      onEdit,
      onFiltersChange,
      onView,
    ],
  );

  const sorting: SortingState = useMemo(() => {
    const [id, direction] = filters.sort.split(',');

    if (!id) {
      return [];
    }

    return [
      {
        id,
        desc: direction === 'desc',
      },
    ];
  }, [filters.sort]);

  const table = useReactTable({
    data,
    columns,
    state: {
      sorting,
    },
    manualPagination: true,
    manualSorting: true,
    pageCount: totalPages,
    getCoreRowModel: getCoreRowModel(),
  });

  const startItem =
    totalElements === 0 ? 0 : filters.page * filters.size + 1;

  const endItem = Math.min(
    (filters.page + 1) * filters.size,
    totalElements,
  );

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-1 flex-col gap-3 sm:flex-row">
          <div className="relative w-full sm:max-w-sm">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />

            <Input
              value={filters.search}
              placeholder="Search meetings..."
              className="pl-9"
              onChange={(event) =>
                onFiltersChange({
                  ...filters,
                  search: event.target.value,
                  page: 0,
                })
              }
            />
          </div>

          <Select
            value={filters.status || ALL_FILTER_VALUE}
            onValueChange={(value) =>
              onFiltersChange({
                ...filters,
                status: value === ALL_FILTER_VALUE ? '' : value,
                page: 0,
              })
            }
          >
            <SelectTrigger className="w-full sm:w-[180px]">
              <SelectValue placeholder="All statuses" />
            </SelectTrigger>

            <SelectContent>
              <SelectItem value={ALL_FILTER_VALUE}>
                All statuses
              </SelectItem>
              <SelectItem value="PLANNED">Planned</SelectItem>
              <SelectItem value="HELD">Held</SelectItem>
              <SelectItem value="NOT_HELD">Not Held</SelectItem>
              <SelectItem value="CANCELLED">Cancelled</SelectItem>
            </SelectContent>
          </Select>

          <Select
            value={filters.meetingType || ALL_FILTER_VALUE}
            onValueChange={(value) =>
              onFiltersChange({
                ...filters,
                meetingType: value === ALL_FILTER_VALUE ? '' : value,
                page: 0,
              })
            }
          >
            <SelectTrigger className="w-full sm:w-[180px]">
              <SelectValue placeholder="All types" />
            </SelectTrigger>

            <SelectContent>
              <SelectItem value={ALL_FILTER_VALUE}>All types</SelectItem>
              <SelectItem value="IN_PERSON">In Person</SelectItem>
              <SelectItem value="VIDEO">Video</SelectItem>
              <SelectItem value="PHONE">Phone</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <Select
          value={String(filters.size)}
          onValueChange={(value) =>
            onFiltersChange({
              ...filters,
              size: Number(value),
              page: 0,
            })
          }
        >
          <SelectTrigger className="w-full sm:w-[130px]">
            <SelectValue />
          </SelectTrigger>

          <SelectContent>
            <SelectItem value="10">10 per page</SelectItem>
            <SelectItem value="20">20 per page</SelectItem>
            <SelectItem value="50">50 per page</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="overflow-hidden rounded-md border">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <TableHead key={header.id} className="whitespace-nowrap">
                      {header.isPlaceholder
                        ? null
                        : flexRender(
                            header.column.columnDef.header,
                            header.getContext(),
                          )}
                    </TableHead>
                  ))}
                </TableRow>
              ))}
            </TableHeader>

            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell
                    colSpan={columns.length}
                    className="h-64 text-center"
                  >
                    <div className="flex items-center justify-center">
                      <Spinner />
                    </div>
                  </TableCell>
                </TableRow>
              ) : table.getRowModel().rows.length === 0 ? (
                <TableRow>
                  <TableCell
                    colSpan={columns.length}
                    className="h-40 text-center"
                  >
                    <div className="flex flex-col items-center justify-center gap-2">
                      <CalendarDays className="h-8 w-8 text-muted-foreground" />

                      <p className="font-medium">No meetings found</p>

                      <p className="text-sm text-muted-foreground">
                        Try changing the filters or create a new meeting.
                      </p>
                    </div>
                  </TableCell>
                </TableRow>
              ) : (
                table.getRowModel().rows.map((row) => (
                  <TableRow
                    key={row.id}
                    className="cursor-pointer"
                    onDoubleClick={() => onView(row.original)}
                  >
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id} className="align-middle">
                        {flexRender(
                          cell.column.columnDef.cell,
                          cell.getContext(),
                        )}
                      </TableCell>
                    ))}
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-muted-foreground">
          Showing {startItem}–{endItem} of {totalElements} meetings
        </p>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={isLoading || filters.page === 0}
            onClick={() =>
              onFiltersChange({
                ...filters,
                page: filters.page - 1,
              })
            }
          >
            <ChevronLeft className="mr-1 h-4 w-4" />
            Previous
          </Button>

          <span className="min-w-24 text-center text-sm">
            Page {totalPages === 0 ? 0 : filters.page + 1} of {totalPages}
          </span>

          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={
              isLoading ||
              totalPages === 0 ||
              filters.page >= totalPages - 1
            }
            onClick={() =>
              onFiltersChange({
                ...filters,
                page: filters.page + 1,
              })
            }
          >
            Next
            <ChevronRight className="ml-1 h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  );
}