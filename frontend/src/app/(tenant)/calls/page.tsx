'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  ArrowDownLeft,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  Clock3,
  ExternalLink,
  Eye,
  FileAudio,
  Link2,
  MoreHorizontal,
  Phone,
  Plus,
  RotateCcw,
  Search,
  UserRound,
  X,
} from 'lucide-react';

import { useCalls } from '@/lib/hooks/calls';
import { usePermissions } from '@/lib/hooks/usePermissions';
import type {
  CallResponse,
  CallStatus,
  CallType,
} from '@/types/calls';

import {
  Alert,
  AlertDescription,
  AlertTitle,
} from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { CallListParams } from '@/lib/api/calls';

interface CallFilters {
  page: number;
  size: number;
  sort: string;
  search: string;
  status: CallStatus | '';
  callType: CallType | '';
}

const INITIAL_FILTERS: CallFilters = {
  page: 0,
  size: 10,
  sort: 'createdAt,desc',
  search: '',
  status: '',
  callType: '',
};

const ALL_FILTER_VALUE = 'ALL';

function formatLabel(value?: string | null) {
  if (!value) {
    return 'Not available';
  }

  return value
    .toLowerCase()
    .split('_')
    .map(
      (part) =>
        part.charAt(0).toUpperCase() +
        part.slice(1)
    )
    .join(' ');
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return 'Not available';
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return 'Not available';
  }

  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function formatDuration(
  durationSeconds?: number | null,
  durationMinutes?: number | null
) {
  if (
    durationSeconds !== null &&
    durationSeconds !== undefined
  ) {
    if (durationSeconds <= 0) {
      return '0 sec';
    }

    const hours = Math.floor(durationSeconds / 3600);
    const minutes = Math.floor(
      (durationSeconds % 3600) / 60
    );
    const seconds = durationSeconds % 60;

    const parts: string[] = [];

    if (hours > 0) {
      parts.push(`${hours}h`);
    }

    if (minutes > 0) {
      parts.push(`${minutes}m`);
    }

    if (seconds > 0 || parts.length === 0) {
      parts.push(`${seconds}s`);
    }

    return parts.join(' ');
  }

  if (
    durationMinutes !== null &&
    durationMinutes !== undefined
  ) {
    return `${durationMinutes} min`;
  }

  return '—';
}

function getStatusBadgeClass(status: CallStatus) {
  switch (status) {
    case 'HELD':
      return [
        'border-emerald-200',
        'bg-emerald-50',
        'text-emerald-700',
        'dark:border-emerald-900',
        'dark:bg-emerald-950',
        'dark:text-emerald-300',
      ].join(' ');

    case 'PLANNED':
      return [
        'border-blue-200',
        'bg-blue-50',
        'text-blue-700',
        'dark:border-blue-900',
        'dark:bg-blue-950',
        'dark:text-blue-300',
      ].join(' ');

    case 'NOT_HELD':
      return [
        'border-amber-200',
        'bg-amber-50',
        'text-amber-700',
        'dark:border-amber-900',
        'dark:bg-amber-950',
        'dark:text-amber-300',
      ].join(' ');

    case 'CANCELLED':
      return [
        'border-red-200',
        'bg-red-50',
        'text-red-700',
        'dark:border-red-900',
        'dark:bg-red-950',
        'dark:text-red-300',
      ].join(' ');

    default:
      return '';
  }
}

function getCallTypeBadgeClass(callType: CallType) {
  if (callType === 'INCOMING') {
    return [
      'border-emerald-200',
      'bg-emerald-50',
      'text-emerald-700',
      'dark:border-emerald-900',
      'dark:bg-emerald-950',
      'dark:text-emerald-300',
    ].join(' ');
  }

  return [
    'border-blue-200',
    'bg-blue-50',
    'text-blue-700',
    'dark:border-blue-900',
    'dark:bg-blue-950',
    'dark:text-blue-300',
  ].join(' ');
}

function isPlayableRecording(
  recordingUrl?: string | null
) {
  if (!recordingUrl) {
    return false;
  }

  const normalized = recordingUrl
    .trim()
    .toLowerCase();

  return (
    normalized !== 'na' &&
    normalized !== 'n/a' &&
    normalized !== 'null' &&
    normalized.startsWith('http')
  );
}

function getEntityRoute(call: CallResponse) {
  if (!call.entityType || !call.entityId) {
    return null;
  }

  switch (call.entityType.toLowerCase()) {
    case 'lead':
      return `/leads/${call.entityId}`;

    case 'contact':
      return `/contacts/${call.entityId}`;

    case 'account':
      return `/accounts/${call.entityId}`;

    case 'deal':
      return `/deals/${call.entityId}`;

    default:
      return null;
  }
}

function getFailureReason(call: CallResponse) {
  const value = call.customData?.failureReason;

  return typeof value === 'string'
    ? value
    : null;
}

function getProviderStatus(call: CallResponse) {
  const value = call.customData?.providerStatus;

  return typeof value === 'string'
    ? value
    : null;
}

function CallsTableSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 7 }).map((_, index) => (
        <div
          key={index}
          className="flex items-center gap-4 rounded-lg border p-4"
        >
          <Skeleton className="h-10 w-10 rounded-full" />

          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-48" />
            <Skeleton className="h-3 w-32" />
          </div>

          <Skeleton className="hidden h-6 w-20 md:block" />
          <Skeleton className="hidden h-6 w-20 lg:block" />
          <Skeleton className="h-8 w-8" />
        </div>
      ))}
    </div>
  );
}

interface MobileCallCardProps {
  call: CallResponse;
  onView: (call: CallResponse) => void;
  onEntityView: (call: CallResponse) => void;
}

function MobileCallCard({
  call,
  onView,
  onEntityView,
}: MobileCallCardProps) {
  const entityRoute = getEntityRoute(call);
  const failureReason = getFailureReason(call);
  const providerStatus = getProviderStatus(call);

  return (
    <Card
      className="cursor-pointer transition-colors hover:bg-muted/30"
      onClick={() => onView(call)}
    >
      <CardContent className="space-y-4 p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="flex min-w-0 items-start gap-3">
            <div
              className={[
                'flex h-10 w-10 shrink-0 items-center justify-center rounded-full',
                call.callType === 'INCOMING'
                  ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300'
                  : 'bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300',
              ].join(' ')}
            >
              {call.callType === 'INCOMING' ? (
                <ArrowDownLeft className="h-5 w-5" />
              ) : (
                <ArrowUpRight className="h-5 w-5" />
              )}
            </div>

            <div className="min-w-0">
              <p className="truncate font-medium">
                {call.subject}
              </p>

              <div className="mt-1 flex items-center gap-1 text-sm text-muted-foreground">
                <Phone className="h-3.5 w-3.5" />
                <span>
                  {call.phoneNumber ?? 'No phone number'}
                </span>
              </div>
            </div>
          </div>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                onClick={(event) =>
                  event.stopPropagation()
                }
              >
                <MoreHorizontal className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>

            <DropdownMenuContent
              align="end"
              onClick={(event) =>
                event.stopPropagation()
              }
            >
              <DropdownMenuItem
                onClick={() => onView(call)}
              >
                <Eye className="mr-2 h-4 w-4" />
                View call
              </DropdownMenuItem>

              {entityRoute && (
                <DropdownMenuItem
                  onClick={() => onEntityView(call)}
                >
                  <Link2 className="mr-2 h-4 w-4" />
                  View linked entity
                </DropdownMenuItem>
              )}

              {isPlayableRecording(
                call.recordingUrl
              ) && (
                <>
                  <DropdownMenuSeparator />

                  <DropdownMenuItem asChild>
                    <a
                      href={call.recordingUrl ?? '#'}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      <FileAudio className="mr-2 h-4 w-4" />
                      Open recording
                    </a>
                  </DropdownMenuItem>
                </>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        <div className="flex flex-wrap gap-2">
          <Badge
            variant="outline"
            className={getStatusBadgeClass(
              call.status
            )}
          >
            {formatLabel(call.status)}
          </Badge>

          <Badge
            variant="outline"
            className={getCallTypeBadgeClass(
              call.callType
            )}
          >
            {formatLabel(call.callType)}
          </Badge>

          {providerStatus && (
            <Badge variant="secondary">
              {formatLabel(providerStatus)}
            </Badge>
          )}
        </div>

        <div className="grid grid-cols-2 gap-3 text-sm">
          <div>
            <p className="text-xs text-muted-foreground">
              Duration
            </p>

            <p className="mt-0.5 font-medium">
              {formatDuration(
                call.durationSeconds,
                call.durationMinutes
              )}
            </p>
          </div>

          <div>
            <p className="text-xs text-muted-foreground">
              Date
            </p>

            <p className="mt-0.5 font-medium">
              {formatDateTime(
                call.startTime ?? call.createdAt
              )}
            </p>
          </div>
        </div>

        {call.entityName && (
          <div className="flex items-center gap-2 rounded-md bg-muted/50 px-3 py-2 text-sm">
            <UserRound className="h-4 w-4 text-muted-foreground" />

            <span className="truncate">
              {call.entityName}
            </span>
          </div>
        )}

        {failureReason && (
          <div className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-300">
            <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" />

            <p className="line-clamp-2">
              {failureReason}
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

export default function CallsPage() {
  const router = useRouter();

  const {
    canViewCalls,
    canEditCalls,
  } = usePermissions();

  const [filters, setFilters] =
    useState<CallFilters>(INITIAL_FILTERS);

  /**
   * This separate value prevents an API request on
   * every single keyboard press.
   */
  const [searchInput, setSearchInput] =
    useState('');

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setFilters((current) => ({
        ...current,
        page: 0,
        search: searchInput.trim(),
      }));
    }, 450);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [searchInput]);

const queryParams = useMemo<CallListParams>(
  () => ({
    page: filters.page,
    size: filters.size,
    sort: filters.sort,
    search: filters.search || undefined,
    status: filters.status || undefined,
    callType: filters.callType || undefined,
  }),
  [filters]
);
  const {
    data,
    isLoading,
    isFetching,
    isError,
    error,
    refetch,
  } = useCalls(queryParams);

  const calls = data?.content ?? [];

  const totalElements = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 0;

  const currentPage =
    data?.number ?? filters.page;

  const pageSize =
    data?.size ?? filters.size;

  const startItem =
    totalElements === 0
      ? 0
      : currentPage * pageSize + 1;

  const endItem = Math.min(
    (currentPage + 1) * pageSize,
    totalElements
  );

  const hasActiveFilters =
    Boolean(filters.search) ||
    Boolean(filters.status) ||
    Boolean(filters.callType);

  if (!canViewCalls) {
    return (
      <div className="flex min-h-64 items-center justify-center rounded-lg border border-dashed">
        <div className="text-center">
          <Phone className="mx-auto mb-3 h-9 w-9 text-muted-foreground" />

          <p className="font-medium">
            Permission required
          </p>

          <p className="mt-1 text-sm text-muted-foreground">
            You don&apos;t have permission to view
            calls.
          </p>
        </div>
      </div>
    );
  }

  const handleCreateNew = () => {
    router.push('/calls/new');
  };

  const handleViewCall = (
    call: CallResponse
  ) => {
    router.push(`/calls/${call.id}`);
  };

  const handleViewEntity = (
    call: CallResponse
  ) => {
    const route = getEntityRoute(call);

    if (route) {
      router.push(route);
    }
  };

  const handleStatusChange = (value: string) => {
  setFilters((current) => ({
    ...current,
    page: 0,
    status:
      value === ALL_FILTER_VALUE
        ? ''
        : (value as CallStatus),
  }));
};

const handleCallTypeChange = (value: string) => {
  setFilters((current) => ({
    ...current,
    page: 0,
    callType:
      value === ALL_FILTER_VALUE
        ? ''
        : (value as CallType),
  }));
};

  const handlePageSizeChange = (
    value: string
  ) => {
    const size = Number(value);

    setFilters((current) => ({
      ...current,
      page: 0,
      size,
    }));
  };

  const handlePreviousPage = () => {
    setFilters((current) => ({
      ...current,
      page: Math.max(0, current.page - 1),
    }));
  };

  const handleNextPage = () => {
    setFilters((current) => ({
      ...current,
      page: Math.min(
        Math.max(totalPages - 1, 0),
        current.page + 1
      ),
    }));
  };

  const handleResetFilters = () => {
    setSearchInput('');

    setFilters((current) => ({
      ...INITIAL_FILTERS,
      size: current.size,
    }));
  };

  const handleClearSearch = () => {
    setSearchInput('');

    setFilters((current) => ({
      ...current,
      page: 0,
      search: '',
    }));
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">
            Calls
          </h1>

          <p className="mt-1 text-muted-foreground">
            Log, track and review incoming and outgoing
            calls.
          </p>
        </div>

        {canEditCalls && (
          <Button
            className="w-full sm:w-auto"
            onClick={handleCreateNew}
          >
            <Plus className="mr-2 h-4 w-4" />
            New Call
          </Button>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              Total Calls
            </CardTitle>

            <Phone className="h-4 w-4 text-muted-foreground" />
          </CardHeader>

          <CardContent>
            <div className="text-2xl font-bold">
              {totalElements}
            </div>

            <p className="text-xs text-muted-foreground">
              Matching current filters
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              Current Page
            </CardTitle>

            <Clock3 className="h-4 w-4 text-muted-foreground" />
          </CardHeader>

          <CardContent>
            <div className="text-2xl font-bold">
              {totalPages === 0
                ? 0
                : currentPage + 1}
            </div>

            <p className="text-xs text-muted-foreground">
              Out of {totalPages} pages
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              Page Records
            </CardTitle>

            <FileAudio className="h-4 w-4 text-muted-foreground" />
          </CardHeader>

          <CardContent>
            <div className="text-2xl font-bold">
              {calls.length}
            </div>

            <p className="text-xs text-muted-foreground">
              Records currently displayed
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              Filter Status
            </CardTitle>

            <Search className="h-4 w-4 text-muted-foreground" />
          </CardHeader>

          <CardContent>
            <div className="text-2xl font-bold">
              {hasActiveFilters ? 'Active' : 'All'}
            </div>

            <p className="text-xs text-muted-foreground">
              {hasActiveFilters
                ? 'Filtered call records'
                : 'No filters applied'}
            </p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardContent className="p-4">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />

              <Input
                value={searchInput}
                onChange={(event) =>
                  setSearchInput(
                    event.target.value
                  )
                }
                className="pl-9 pr-9"
                placeholder="Search by subject, phone number or entity..."
              />

              {searchInput && (
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="absolute right-1 top-1/2 h-8 w-8 -translate-y-1/2"
                  onClick={handleClearSearch}
                >
                  <X className="h-4 w-4" />
                </Button>
              )}
            </div>

            <Select
              value={
                filters.status ||
                ALL_FILTER_VALUE
              }
              onValueChange={handleStatusChange}
            >
              <SelectTrigger className="w-full lg:w-[180px]">
                <SelectValue placeholder="All statuses" />
              </SelectTrigger>

              <SelectContent>
                <SelectItem value={ALL_FILTER_VALUE}>
                  All statuses
                </SelectItem>

                <SelectItem value="PLANNED">
                  Planned
                </SelectItem>

                <SelectItem value="HELD">
                  Held
                </SelectItem>

                <SelectItem value="NOT_HELD">
                  Not held
                </SelectItem>

                <SelectItem value="CANCELLED">
                  Cancelled
                </SelectItem>
              </SelectContent>
            </Select>

            <Select
              value={
                filters.callType ||
                ALL_FILTER_VALUE
              }
              onValueChange={
                handleCallTypeChange
              }
            >
              <SelectTrigger className="w-full lg:w-[180px]">
                <SelectValue placeholder="All call types" />
              </SelectTrigger>

              <SelectContent>
                <SelectItem value={ALL_FILTER_VALUE}>
                  All call types
                </SelectItem>

                <SelectItem value="INCOMING">
                  Incoming
                </SelectItem>

                <SelectItem value="OUTGOING">
                  Outgoing
                </SelectItem>
              </SelectContent>
            </Select>

            {hasActiveFilters && (
              <Button
                variant="outline"
                onClick={handleResetFilters}
              >
                <RotateCcw className="mr-2 h-4 w-4" />
                Reset
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      {isError && (
        <Alert variant="destructive">
          <CircleAlert className="h-4 w-4" />

          <AlertTitle>
            Unable to load calls
          </AlertTitle>

          <AlertDescription className="flex flex-col items-start gap-3 sm:flex-row sm:items-center sm:justify-between">
            <span>
              {error instanceof Error
                ? error.message
                : 'An unexpected error occurred while loading calls.'}
            </span>

            <Button
              variant="outline"
              size="sm"
              onClick={() => refetch()}
            >
              Try again
            </Button>
          </AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0">
          <div>
            <CardTitle>Call history</CardTitle>

            <p className="mt-1 text-sm text-muted-foreground">
              View call results, recordings and linked
              CRM records.
            </p>
          </div>

          {isFetching && !isLoading && (
            <Badge variant="secondary">
              Refreshing...
            </Badge>
          )}
        </CardHeader>

        <CardContent>
          {isLoading ? (
            <CallsTableSkeleton />
          ) : calls.length === 0 ? (
            <div className="flex min-h-72 flex-col items-center justify-center rounded-lg border border-dashed p-8 text-center">
              <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-muted">
                <Phone className="h-6 w-6 text-muted-foreground" />
              </div>

              <h3 className="font-semibold">
                No calls found
              </h3>

              <p className="mt-1 max-w-md text-sm text-muted-foreground">
                {hasActiveFilters
                  ? 'No calls match your current search and filter criteria.'
                  : 'No calls have been created or received yet.'}
              </p>

              <div className="mt-4 flex flex-wrap justify-center gap-2">
                {hasActiveFilters && (
                  <Button
                    variant="outline"
                    onClick={handleResetFilters}
                  >
                    <RotateCcw className="mr-2 h-4 w-4" />
                    Reset filters
                  </Button>
                )}

                {canEditCalls &&
                  !hasActiveFilters && (
                    <Button
                      onClick={handleCreateNew}
                    >
                      <Plus className="mr-2 h-4 w-4" />
                      Create call
                    </Button>
                  )}
              </div>
            </div>
          ) : (
            <>
              <div className="space-y-3 md:hidden">
                {calls.map((call) => (
                  <MobileCallCard
                    key={call.id}
                    call={call}
                    onView={handleViewCall}
                    onEntityView={
                      handleViewEntity
                    }
                  />
                ))}
              </div>

              <div className="hidden overflow-hidden rounded-md border md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Call</TableHead>
                      <TableHead>Type</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Linked Entity</TableHead>
                      <TableHead>Duration</TableHead>
                      <TableHead>Provider</TableHead>
                      <TableHead>Date</TableHead>
                      <TableHead className="w-[60px] text-right">
                        Actions
                      </TableHead>
                    </TableRow>
                  </TableHeader>

                  <TableBody>
                    {calls.map((call) => {
                      const entityRoute =
                        getEntityRoute(call);

                      const failureReason =
                        getFailureReason(call);

                      const providerStatus =
                        getProviderStatus(call);

                      const recordingAvailable =
                        isPlayableRecording(
                          call.recordingUrl
                        );

                      return (
                        <TableRow
                          key={call.id}
                          className="cursor-pointer"
                          onClick={() =>
                            handleViewCall(call)
                          }
                        >
                          <TableCell>
                            <div className="flex items-center gap-3">
                              <div
                                className={[
                                  'flex h-9 w-9 shrink-0 items-center justify-center rounded-full',
                                  call.callType ===
                                  'INCOMING'
                                    ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300'
                                    : 'bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300',
                                ].join(' ')}
                              >
                                {call.callType ===
                                'INCOMING' ? (
                                  <ArrowDownLeft className="h-4 w-4" />
                                ) : (
                                  <ArrowUpRight className="h-4 w-4" />
                                )}
                              </div>

                              <div className="min-w-0">
                                <div className="flex items-center gap-2">
                                  <p className="max-w-[220px] truncate font-medium">
                                    {call.subject}
                                  </p>

                                  {recordingAvailable && (
                                    <FileAudio className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                                  )}

                                  {failureReason && (
                                    <CircleAlert className="h-3.5 w-3.5 shrink-0 text-red-500" />
                                  )}
                                </div>

                                <p className="mt-0.5 text-sm text-muted-foreground">
                                  {call.phoneNumber ??
                                    'No phone number'}
                                </p>
                              </div>
                            </div>
                          </TableCell>

                          <TableCell>
                            <Badge
                              variant="outline"
                              className={getCallTypeBadgeClass(
                                call.callType
                              )}
                            >
                              {call.callType ===
                              'INCOMING' ? (
                                <ArrowDownLeft className="mr-1 h-3 w-3" />
                              ) : (
                                <ArrowUpRight className="mr-1 h-3 w-3" />
                              )}

                              {formatLabel(
                                call.callType
                              )}
                            </Badge>
                          </TableCell>

                          <TableCell>
                            <div className="space-y-1">
                              <Badge
                                variant="outline"
                                className={getStatusBadgeClass(
                                  call.status
                                )}
                              >
                                {formatLabel(
                                  call.status
                                )}
                              </Badge>

                              {providerStatus && (
                                <p className="text-xs text-muted-foreground">
                                  Provider:{' '}
                                  {formatLabel(
                                    providerStatus
                                  )}
                                </p>
                              )}
                            </div>
                          </TableCell>

                          <TableCell>
                            {call.entityName ? (
                              <button
                                type="button"
                                className="max-w-[180px] text-left"
                                onClick={(event) => {
                                  event.stopPropagation();

                                  if (entityRoute) {
                                    router.push(
                                      entityRoute
                                    );
                                  }
                                }}
                              >
                                <p className="truncate font-medium hover:underline">
                                  {call.entityName}
                                </p>

                                <p className="text-xs text-muted-foreground">
                                  {formatLabel(
                                    call.entityType
                                  )}
                                </p>
                              </button>
                            ) : (
                              <span className="text-sm text-muted-foreground">
                                Not linked
                              </span>
                            )}
                          </TableCell>

                          <TableCell>
                            <div className="flex items-center gap-1.5">
                              <Clock3 className="h-3.5 w-3.5 text-muted-foreground" />

                              <span>
                                {formatDuration(
                                  call.durationSeconds,
                                  call.durationMinutes
                                )}
                              </span>
                            </div>
                          </TableCell>

                          <TableCell>
                            <div className="max-w-[150px]">
                              <p className="truncate text-sm">
                                {call.providerName ??
                                  'Not available'}
                              </p>

                              {call.externalCallId && (
                                <p
                                  className="truncate text-xs text-muted-foreground"
                                  title={
                                    call.externalCallId
                                  }
                                >
                                  {
                                    call.externalCallId
                                  }
                                </p>
                              )}
                            </div>
                          </TableCell>

                          <TableCell>
                            <div className="min-w-[145px]">
                              <p className="text-sm">
                                {formatDateTime(
                                  call.startTime ??
                                    call.createdAt
                                )}
                              </p>

                              {call.assigneeName && (
                                <p className="mt-0.5 text-xs text-muted-foreground">
                                  {call.assigneeName}
                                </p>
                              )}
                            </div>
                          </TableCell>

                          <TableCell className="text-right">
                            <DropdownMenu>
                              <DropdownMenuTrigger
                                asChild
                              >
                                <Button
                                  variant="ghost"
                                  size="icon"
                                  onClick={(event) =>
                                    event.stopPropagation()
                                  }
                                >
                                  <MoreHorizontal className="h-4 w-4" />

                                  <span className="sr-only">
                                    Open call actions
                                  </span>
                                </Button>
                              </DropdownMenuTrigger>

                              <DropdownMenuContent
                                align="end"
                                onClick={(event) =>
                                  event.stopPropagation()
                                }
                              >
                                <DropdownMenuItem
                                  onClick={() =>
                                    handleViewCall(
                                      call
                                    )
                                  }
                                >
                                  <Eye className="mr-2 h-4 w-4" />
                                  View call
                                </DropdownMenuItem>

                                {entityRoute && (
                                  <DropdownMenuItem
                                    onClick={() =>
                                      handleViewEntity(
                                        call
                                      )
                                    }
                                  >
                                    <Link2 className="mr-2 h-4 w-4" />
                                    View linked entity
                                  </DropdownMenuItem>
                                )}

                                {recordingAvailable && (
                                  <>
                                    <DropdownMenuSeparator />

                                    <DropdownMenuItem
                                      asChild
                                    >
                                      <a
                                        href={
                                          call.recordingUrl ??
                                          '#'
                                        }
                                        target="_blank"
                                        rel="noopener noreferrer"
                                      >
                                        <FileAudio className="mr-2 h-4 w-4" />
                                        Open recording
                                        <ExternalLink className="ml-auto h-3.5 w-3.5" />
                                      </a>
                                    </DropdownMenuItem>
                                  </>
                                )}
                              </DropdownMenuContent>
                            </DropdownMenu>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
            </>
          )}

          {!isLoading && totalElements > 0 && (
            <div className="mt-4 flex flex-col gap-4 border-t pt-4 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-sm text-muted-foreground">
                Showing{' '}
                <span className="font-medium text-foreground">
                  {startItem}
                </span>{' '}
                to{' '}
                <span className="font-medium text-foreground">
                  {endItem}
                </span>{' '}
                of{' '}
                <span className="font-medium text-foreground">
                  {totalElements}
                </span>{' '}
                calls
              </p>

              <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                <div className="flex items-center gap-2">
                  <span className="whitespace-nowrap text-sm text-muted-foreground">
                    Rows per page
                  </span>

                  <Select
                    value={String(filters.size)}
                    onValueChange={
                      handlePageSizeChange
                    }
                  >
                    <SelectTrigger className="h-9 w-[76px]">
                      <SelectValue />
                    </SelectTrigger>

                    <SelectContent>
                      <SelectItem value="5">
                        5
                      </SelectItem>

                      <SelectItem value="10">
                        10
                      </SelectItem>

                      <SelectItem value="20">
                        20
                      </SelectItem>

                      <SelectItem value="50">
                        50
                      </SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="flex items-center justify-between gap-2">
                  <span className="whitespace-nowrap text-sm text-muted-foreground">
                    Page{' '}
                    {totalPages === 0
                      ? 0
                      : currentPage + 1}{' '}
                    of {totalPages}
                  </span>

                  <div className="flex items-center gap-1">
                    <Button
                      variant="outline"
                      size="icon"
                      disabled={
                        currentPage <= 0 ||
                        isFetching
                      }
                      onClick={
                        handlePreviousPage
                      }
                    >
                      <ChevronLeft className="h-4 w-4" />

                      <span className="sr-only">
                        Previous page
                      </span>
                    </Button>

                    <Button
                      variant="outline"
                      size="icon"
                      disabled={
                        currentPage >=
                          totalPages - 1 ||
                        totalPages === 0 ||
                        isFetching
                      }
                      onClick={handleNextPage}
                    >
                      <ChevronRight className="h-4 w-4" />

                      <span className="sr-only">
                        Next page
                      </span>
                    </Button>
                  </div>
                </div>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}