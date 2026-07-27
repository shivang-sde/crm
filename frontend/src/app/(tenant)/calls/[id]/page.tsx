'use client';

import { useParams, useRouter } from 'next/navigation';
import {
  ArrowDownLeft,
  ArrowLeft,
  ArrowUpRight,
  CalendarClock,
  Clock3,
  Copy,
  ExternalLink,
  FileAudio,
  Headphones,
  Link2,
  Pencil,
  Phone,
  Server,
  Trash2,
  UserRound,
} from 'lucide-react';

import { toast } from 'sonner';

import {
  useCall,
  useDeleteCall,
} from '@/lib/hooks/calls';
import { usePermissions } from '@/lib/hooks/usePermissions';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return 'Not available';
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return 'Not available';
  }

  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(date);
}

function formatDuration(
  durationSeconds: number | null,
  durationMinutes: number | null
) {
  if (
    durationSeconds !== null &&
    durationSeconds !== undefined
  ) {
    if (durationSeconds <= 0) {
      return '0 seconds';
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
    return `${durationMinutes} minute${
      durationMinutes === 1 ? '' : 's'
    }`;
  }

  return 'Not available';
}

function formatLabel(value: string | null | undefined) {
  if (!value) {
    return 'Not available';
  }

  return value
    .toLowerCase()
    .split('_')
    .map(
      (part) =>
        part.charAt(0).toUpperCase() + part.slice(1)
    )
    .join(' ');
}

function getStatusClass(status: string) {
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

function isPlayableRecording(
  recordingUrl: string | null | undefined
) {
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

function getEntityRoute(
  entityType: string | null,
  entityId: string | null
) {
  if (!entityType || !entityId) {
    return null;
  }

  switch (entityType.toLowerCase()) {
    case 'lead':
      return `/leads/${entityId}`;

    case 'contact':
      return `/contacts/${entityId}`;

    case 'account':
      return `/accounts/${entityId}`;

    case 'deal':
      return `/deals/${entityId}`;

    default:
      return null;
  }
}

interface DetailItemProps {
  label: string;
  value: React.ReactNode;
}

function DetailItem({ label, value }: DetailItemProps) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </p>

      <div className="text-sm font-medium">
        {value}
      </div>
    </div>
  );
}

function CallDetailSkeleton() {
  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-center gap-4">
          <Skeleton className="h-10 w-10 rounded-md" />

          <div className="space-y-2">
            <Skeleton className="h-8 w-72" />
            <Skeleton className="h-4 w-44" />
          </div>
        </div>

        <Skeleton className="h-10 w-32" />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Skeleton className="h-72 lg:col-span-2" />
        <Skeleton className="h-72" />
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Skeleton className="h-56" />
        <Skeleton className="h-56" />
      </div>
    </div>
  );
}

export default function CallDetailPage() {
  const router = useRouter();

  const params = useParams<{
    id?: string | string[];
  }>();

  const rawId = params?.id;

  const id =
    typeof rawId === 'string'
      ? rawId
      : rawId?.[0] ?? '';

  const {
    canEditCalls,
    canDeleteCalls,
  } = usePermissions();

  const {
    data: call,
    isLoading,
    isError,
    error,
  } = useCall(id);

  console.log('Call data:', call);

  const deleteCall = useDeleteCall();

  if (isLoading) {
    return <CallDetailSkeleton />;
  }

  if (isError) {
    return (
      <div className="space-y-4">
        <Button
          variant="outline"
          onClick={() => router.push('/calls')}
        >
          <ArrowLeft className="mr-2 h-4 w-4" />
          Back to calls
        </Button>

        <Alert variant="destructive">
          <AlertTitle>Unable to load call</AlertTitle>

          <AlertDescription>
            {error instanceof Error
              ? error.message
              : 'An unexpected error occurred while loading the call.'}
          </AlertDescription>
        </Alert>
      </div>
    );
  }

  if (!call) {
    return (
      <div className="flex min-h-64 flex-col items-center justify-center gap-4 rounded-lg border border-dashed">
        <Phone className="h-10 w-10 text-muted-foreground" />

        <div className="text-center">
          <h2 className="font-semibold">Call not found</h2>

          <p className="text-sm text-muted-foreground">
            The requested call may have been deleted or does
            not exist.
          </p>
        </div>

        <Button
          variant="outline"
          onClick={() => router.push('/calls')}
        >
          <ArrowLeft className="mr-2 h-4 w-4" />
          Back to calls
        </Button>
      </div>
    );
  }

  const entityRoute = getEntityRoute(
    call.entityType,
    call.entityId
  );

  const providerStatus =
    typeof call.customData?.providerStatus === 'string'
      ? call.customData.providerStatus
      : null;

  const failureReason =
    typeof call.customData?.failureReason === 'string'
      ? call.customData.failureReason
      : null;

  const failedAt =
    typeof call.customData?.failedAt === 'string'
      ? call.customData.failedAt
      : null;

  const playableRecording = isPlayableRecording(
    call.recordingUrl
  );

  const handleDelete = async () => {
    const confirmed = window.confirm(
      'Are you sure you want to delete this call?'
    );

    if (!confirmed) {
      return;
    }

    try {
      await deleteCall.mutateAsync(call.id);

      toast.success('Call deleted successfully');

      router.push('/calls');
    } catch (deleteError) {
      toast.error(
        deleteError instanceof Error
          ? deleteError.message
          : 'Failed to delete call'
      );
    }
  };

  const handleCopyPhone = async () => {
    if (!call.phoneNumber) {
      return;
    }

    try {
      await navigator.clipboard.writeText(
        call.phoneNumber
      );

      toast.success('Phone number copied');
    } catch {
      toast.error('Unable to copy phone number');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-4 lg:flex-row lg:items-start">
        <div className="flex min-w-0 items-start gap-3">
          <Button
            variant="outline"
            size="icon"
            className="shrink-0"
            onClick={() => router.push('/calls')}
            aria-label="Back to calls"
          >
            <ArrowLeft className="h-4 w-4" />
          </Button>

          <div className="min-w-0 space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="truncate text-2xl font-bold tracking-tight sm:text-3xl">
                {call.subject}
              </h1>

              <Badge
                variant="outline"
                className={getStatusClass(call.status)}
              >
                {formatLabel(call.status)}
              </Badge>
            </div>

            <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-muted-foreground">
              <span className="inline-flex items-center gap-1.5">
                {call.callType === 'INCOMING' ? (
                  <ArrowDownLeft className="h-4 w-4 text-emerald-600" />
                ) : (
                  <ArrowUpRight className="h-4 w-4 text-blue-600" />
                )}

                {formatLabel(call.callType)}
              </span>

              {call.phoneNumber && (
                <span className="inline-flex items-center gap-1.5">
                  <Phone className="h-4 w-4" />
                  {call.phoneNumber}
                </span>
              )}

              <span className="inline-flex items-center gap-1.5">
                <Clock3 className="h-4 w-4" />

                {formatDuration(
                  call.durationSeconds,
                  call.durationMinutes
                )}
              </span>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {call.phoneNumber && (
            <Button
              variant="outline"
              onClick={handleCopyPhone}
            >
              <Copy className="mr-2 h-4 w-4" />
              Copy number
            </Button>
          )}

          {canEditCalls && (
            <Button
              variant="outline"
              onClick={() =>
                router.push(`/calls/${call.id}/edit`)
              }
            >
              <Pencil className="mr-2 h-4 w-4" />
              Edit
            </Button>
          )}

          {canDeleteCalls && (
            <Button
              variant="destructive"
              disabled={deleteCall.isPending}
              onClick={handleDelete}
            >
              <Trash2 className="mr-2 h-4 w-4" />

              {deleteCall.isPending
                ? 'Deleting...'
                : 'Delete'}
            </Button>
          )}
        </div>
      </div>

      {failureReason && (
        <Alert variant="destructive">
          <Server className="h-4 w-4" />

          <AlertTitle>
            Provider execution failed
          </AlertTitle>

          <AlertDescription className="space-y-1">
            <p>{failureReason}</p>

            {failedAt && (
              <p className="text-xs">
                Failed at {formatDateTime(failedAt)}
              </p>
            )}
          </AlertDescription>
        </Alert>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Call information</CardTitle>

            <CardDescription>
              Primary details and current call status
            </CardDescription>
          </CardHeader>

          <CardContent>
            <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              <DetailItem
                label="Call type"
                value={
                  <span className="inline-flex items-center gap-2">
                    {call.callType === 'INCOMING' ? (
                      <ArrowDownLeft className="h-4 w-4 text-emerald-600" />
                    ) : (
                      <ArrowUpRight className="h-4 w-4 text-blue-600" />
                    )}

                    {formatLabel(call.callType)}
                  </span>
                }
              />

              <DetailItem
                label="Status"
                value={
                  <Badge
                    variant="outline"
                    className={getStatusClass(call.status)}
                  >
                    {formatLabel(call.status)}
                  </Badge>
                }
              />

              <DetailItem
                label="Phone number"
                value={call.phoneNumber ?? 'Not available'}
              />

              <DetailItem
                label="Duration"
                value={formatDuration(
                  call.durationSeconds,
                  call.durationMinutes
                )}
              />

              <DetailItem
                label="Disposition"
                value={
                  call.disposition ?? 'Not captured'
                }
              />

              <DetailItem
                label="Next action"
                value={
                  call.nextAction ?? 'No next action'
                }
              />
            </div>

            {(call.description || call.notes) && (
              <>
                <Separator className="my-6" />

                <div className="grid gap-6 md:grid-cols-2">
                  {call.description && (
                    <DetailItem
                      label="Description"
                      value={
                        <p className="whitespace-pre-wrap font-normal leading-6">
                          {call.description}
                        </p>
                      }
                    />
                  )}

                  {call.notes && (
                    <DetailItem
                      label="Notes"
                      value={
                        <p className="whitespace-pre-wrap font-normal leading-6">
                          {call.notes}
                        </p>
                      }
                    />
                  )}
                </div>
              </>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Recording</CardTitle>

            <CardDescription>
              Listen to the call recording
            </CardDescription>
          </CardHeader>

          <CardContent>
            {playableRecording ? (
              <div className="space-y-4">
                <div className="flex items-center gap-3 rounded-lg border bg-muted/30 p-3">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary/10">
                    <Headphones className="h-5 w-5 text-primary" />
                  </div>

                  <div className="min-w-0">
                    <p className="text-sm font-medium">
                      Call recording
                    </p>

                    <p className="truncate text-xs text-muted-foreground">
                      {call.externalCallId ??
                        'Provider recording'}
                    </p>
                  </div>
                </div>

                <audio
                  controls
                  preload="metadata"
                  className="w-full"
                  src={call.recordingUrl ?? undefined}
                >
                  Your browser does not support audio
                  playback.
                </audio>

                <Button
                  variant="outline"
                  className="w-full"
                  asChild
                >
                  <a
                    href={call.recordingUrl ?? '#'}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <FileAudio className="mr-2 h-4 w-4" />
                    Open recording
                    <ExternalLink className="ml-2 h-3.5 w-3.5" />
                  </a>
                </Button>
              </div>
            ) : (
              <div className="flex min-h-32 flex-col items-center justify-center gap-2 rounded-lg border border-dashed p-6 text-center">
                <FileAudio className="h-8 w-8 text-muted-foreground" />

                <p className="text-sm font-medium">
                  Recording unavailable
                </p>

                <p className="text-xs text-muted-foreground">
                  No playable recording was returned by
                  the provider.
                </p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Timeline</CardTitle>

            <CardDescription>
              Call and follow-up timestamps
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-5">
            <DetailItem
              label="Started at"
              value={formatDateTime(call.startTime)}
            />

            <DetailItem
              label="Ended at"
              value={formatDateTime(call.endTime)}
            />

            <DetailItem
              label="Follow-up at"
              value={formatDateTime(call.followUpAt)}
            />

            <DetailItem
              label="Reminder"
              value={formatDateTime(call.remindAt)}
            />

            <Separator />

            <DetailItem
              label="Created at"
              value={formatDateTime(call.createdAt)}
            />

            <DetailItem
              label="Last updated"
              value={formatDateTime(call.updatedAt)}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Linking and ownership</CardTitle>

            <CardDescription>
              CRM entity and responsible user
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-5">
            <DetailItem
              label="Linked entity"
              value={
                call.entityName ||
                call.entityType ||
                'Not linked'
              }
            />

            <DetailItem
              label="Entity type"
              value={formatLabel(call.entityType)}
            />

            {entityRoute && (
              <Button
                variant="outline"
                className="w-full justify-start"
                onClick={() => router.push(entityRoute)}
              >
                <Link2 className="mr-2 h-4 w-4" />
                Open linked {call.entityType}
              </Button>
            )}

            <Separator />

            <DetailItem
              label="Assigned to"
              value={
                call.assigneeName ??
                call.assignedTo ??
                'Unassigned'
              }
            />

            <DetailItem
              label="Owner"
              value={
                call.ownerUserId ?? 'No owner assigned'
              }
            />

            <DetailItem
              label="Created by"
              value={call.createdBy ?? 'System'}
            />
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Provider information</CardTitle>

          <CardDescription>
            Integration and external call metadata
          </CardDescription>
        </CardHeader>

        <CardContent>
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
            <DetailItem
              label="Provider"
              value={call.providerName ?? 'Not available'}
            />

            <DetailItem
              label="External call ID"
              value={
                call.externalCallId ?? 'Not available'
              }
            />

            <DetailItem
              label="Provider status"
              value={
                providerStatus ? (
                  <Badge
                    variant={
                      providerStatus.toLowerCase() ===
                      'success'
                        ? 'secondary'
                        : 'destructive'
                    }
                  >
                    {formatLabel(providerStatus)}
                  </Badge>
                ) : (
                  'Not available'
                )
              }
            />

            <DetailItem
  label="Created source"
  value={call.createdBy ? 'User' : 'System'}
/>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}