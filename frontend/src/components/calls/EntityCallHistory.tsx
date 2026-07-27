'use client';

import { useRouter } from 'next/navigation';
import {
  ArrowDownLeft,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  Clock3,
  ExternalLink,
  FileAudio,
  Phone,
} from 'lucide-react';

import { useCalls } from '@/lib/hooks/calls';
import type {
  CallResponse,
  CallStatus,
} from '@/types/calls';

import { Alert, AlertDescription } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useState } from 'react';

interface EntityCallHistoryProps {
  entityType: 'lead' | 'contact' | 'account' | 'deal';
  entityId: string;
  title?: string;
  pageSize?: number;
}

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
  seconds?: number | null,
  minutes?: number | null
) {
  if (seconds !== null && seconds !== undefined) {
    if (seconds <= 0) {
      return '0 sec';
    }

    const calculatedMinutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;

    if (calculatedMinutes === 0) {
      return `${remainingSeconds} sec`;
    }

    if (remainingSeconds === 0) {
      return `${calculatedMinutes} min`;
    }

    return `${calculatedMinutes}m ${remainingSeconds}s`;
  }

  if (minutes !== null && minutes !== undefined) {
    return `${minutes} min`;
  }

  return 'Not available';
}

function getStatusClass(status: CallStatus) {
  switch (status) {
    case 'HELD':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950 dark:text-emerald-300';

    case 'PLANNED':
      return 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-900 dark:bg-blue-950 dark:text-blue-300';

    case 'NOT_HELD':
      return 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-300';

    case 'CANCELLED':
      return 'border-red-200 bg-red-50 text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-300';

    default:
      return '';
  }
}

function hasRecording(recordingUrl?: string | null) {
  if (!recordingUrl) {
    return false;
  }

  const normalized = recordingUrl.trim().toLowerCase();

  return (
    normalized !== 'na' &&
    normalized !== 'n/a' &&
    normalized !== 'null' &&
    normalized.startsWith('http')
  );
}

function getFailureReason(call: CallResponse) {
  const reason = call.customData?.failureReason;

  return typeof reason === 'string'
    ? reason
    : null;
}

function HistorySkeleton() {
  return (
    <div className="space-y-4">
      {Array.from({ length: 3 }).map((_, index) => (
        <div
          key={index}
          className="flex gap-3 rounded-lg border p-4"
        >
          <Skeleton className="h-10 w-10 shrink-0 rounded-full" />

          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-44" />
            <Skeleton className="h-3 w-64" />
            <Skeleton className="h-3 w-32" />
          </div>
        </div>
      ))}
    </div>
  );
}

interface CallHistoryItemProps {
  call: CallResponse;
}

function CallHistoryItem({
  call,
}: CallHistoryItemProps) {

    
  const router = useRouter();

  const failureReason = getFailureReason(call);
  const recordingAvailable = hasRecording(
    call.recordingUrl
  );

  return (
    <div
      role="button"
      tabIndex={0}
      className="group rounded-lg border p-4 transition-colors hover:bg-muted/40"
      onClick={() => router.push(`/calls/${call.id}`)}
      onKeyDown={(event) => {
        if (
          event.key === 'Enter' ||
          event.key === ' '
        ) {
          router.push(`/calls/${call.id}`);
        }
      }}
    >
      <div className="flex items-start gap-3">
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

        <div className="min-w-0 flex-1">
          <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-start">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h4 className="truncate font-medium">
                  {call.subject}
                </h4>

                <Badge
                  variant="outline"
                  className={getStatusClass(call.status)}
                >
                  {formatLabel(call.status)}
                </Badge>
              </div>

              <div className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
                {call.phoneNumber && (
                  <span className="inline-flex items-center gap-1">
                    <Phone className="h-3.5 w-3.5" />
                    {call.phoneNumber}
                  </span>
                )}

                <span className="inline-flex items-center gap-1">
                  <Clock3 className="h-3.5 w-3.5" />

                  {formatDuration(
                    call.durationSeconds,
                    call.durationMinutes
                  )}
                </span>

                <span>
                  {formatLabel(call.callType)}
                </span>
              </div>
            </div>

            <time className="shrink-0 text-xs text-muted-foreground">
              {formatDateTime(
                call.startTime ?? call.createdAt
              )}
            </time>
          </div>

          {(call.providerName ||
            call.disposition ||
            recordingAvailable) && (
            <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
              {call.providerName && (
                <span className="text-muted-foreground">
                  Via {call.providerName}
                </span>
              )}

              {call.disposition && (
                <span>
                  <span className="text-muted-foreground">
                    Disposition:
                  </span>{' '}
                  {formatLabel(call.disposition)}
                </span>
              )}

              {recordingAvailable && (
                <a
                  href={call.recordingUrl ?? '#'}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-primary hover:underline"
                  onClick={(event) =>
                    event.stopPropagation()
                  }
                >
                  <FileAudio className="h-4 w-4" />
                  Recording
                  <ExternalLink className="h-3 w-3" />
                </a>
              )}
            </div>
          )}

          {call.notes && (
            <p className="mt-3 line-clamp-2 text-sm text-muted-foreground">
              {call.notes}
            </p>
          )}

          {failureReason && (
            <div className="mt-3 flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-300">
              <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" />
              <span className="line-clamp-2">
                {failureReason}
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export function EntityCallHistory({
  entityType,
  entityId,
  title = 'Call history',
  pageSize = 5,
}: EntityCallHistoryProps) {

    const [page, setPage] = useState(0);

  const {
    data,
    isLoading,
    isFetching,
    isError,
  } = useCalls({
    entityType,
    entityId,
    page: 0,
    size: pageSize,
    sort: 'createdAt,desc',
  });

  const calls = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-4">
          <div>
            <CardTitle>{title}</CardTitle>

            <CardDescription>
              Calls linked to this {entityType}.
            </CardDescription>
          </div>

          {totalElements > 0 && (
            <Badge variant="secondary">
              {totalElements}
            </Badge>
          )}
        </div>
      </CardHeader>

      <CardContent>
        {isLoading ? (
          <HistorySkeleton />
        ) : isError ? (
          <Alert variant="destructive">
            <CircleAlert className="h-4 w-4" />

            <AlertDescription>
              Unable to load linked calls.
            </AlertDescription>
          </Alert>
        ) : calls.length === 0 ? (
          <div className="flex min-h-40 flex-col items-center justify-center rounded-lg border border-dashed p-6 text-center">
            <Phone className="mb-3 h-8 w-8 text-muted-foreground" />

            <p className="font-medium">
              No linked calls
            </p>

            <p className="mt-1 text-sm text-muted-foreground">
              Calls linked to this {entityType} will
              appear here.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {calls.map((call) => (
              <CallHistoryItem
                key={call.id}
                call={call}
              />
            ))}

            {isFetching && (
              <p className="text-center text-xs text-muted-foreground">
                Refreshing calls...
              </p>
            )}
          </div>
        )}
      </CardContent>

      {(data?.totalPages ?? 0) > 1 && (
  <div className="flex items-center justify-between border-t pt-4">
    <p className="text-sm text-muted-foreground">
      Page {(data?.number ?? page) + 1} of{' '}
      {data?.totalPages ?? 0}
    </p>

    <div className="flex gap-2">
      <Button
        variant="outline"
        size="sm"
        disabled={
          page === 0 || isFetching
        }
        onClick={() =>
          setPage((current) =>
            Math.max(0, current - 1)
          )
        }
      >
        <ChevronLeft className="mr-1 h-4 w-4" />
        Previous
      </Button>

      <Button
        variant="outline"
        size="sm"
        disabled={
          data?.last ||
          isFetching
        }
        onClick={() =>
          setPage((current) => current + 1)
        }
      >
        Next
        <ChevronRight className="ml-1 h-4 w-4" />
      </Button>
    </div>
  </div>
)}
    </Card>

    
  );
}