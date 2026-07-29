'use client';

import React from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  ArrowDownLeft,
  ArrowLeft,
  ArrowUpRight,
  Building2,
  CheckCircle2,
  Clock3,
  Copy,
  ExternalLink,
  FileAudio,
  Headphones,
  Link2,
  Mail,
  MapPin,
  Phone,
  Server,
  UserRound,
} from 'lucide-react';
import { toast } from 'sonner';

import { useCall } from '@/lib/hooks/calls';
import { useLead } from '@/lib/hooks/leads';
import { useContact } from '@/lib/hooks/contacts';
import { useAccount } from '@/lib/hooks/accounts';
// Add this import when the deal hook exists:
// import { useDeal } from '@/lib/hooks/deals';

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

import UnknownCallerWorkflow from '@/components/calls/UnknownCallerWorkflow';
import { CallDispositionForm } from '@/components/calls/CallDispositionForm';

import {
  formatDateTime,
  formatDuration,
} from '@/lib/utils';
import { useDeal } from '@/lib/hooks/deals';

interface DetailItemProps {
  label: string;
  value: React.ReactNode;
}

function DetailItem({
  label,
  value,
}: DetailItemProps) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </p>

      <div className="break-words text-sm font-medium">
        {value}
      </div>
    </div>
  );
}

function formatLabel(
  value: string | null | undefined,
): string {
  if (!value) {
    return 'Not available';
  }

  return value
    .toLowerCase()
    .split('_')
    .map(
      (part) =>
        part.charAt(0).toUpperCase() +
        part.slice(1),
    )
    .join(' ');
}

function getStatusClass(status: string): string {
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
      return [
        'border-slate-200',
        'bg-slate-50',
        'text-slate-700',
        'dark:border-slate-800',
        'dark:bg-slate-950',
        'dark:text-slate-300',
      ].join(' ');
  }
}

function getCallTypeClass(
  callType: string,
): string {
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
  recordingUrl: string | null | undefined,
): boolean {
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

function getEntityRoute(
  entityType: string | null | undefined,
  entityId: string | null | undefined,
): string | null {
  if (!entityType || !entityId) {
    return null;
  }

  switch (entityType.toUpperCase()) {
    case 'LEAD':
      return `/leads/${entityId}`;

    case 'CONTACT':
      return `/contacts/${entityId}`;

    case 'ACCOUNT':
      return `/accounts/${entityId}`;

    case 'DEAL':
      return `/deals/${entityId}`;

    default:
      return null;
  }
}

function getStringValue(
  source: unknown,
  ...keys: string[]
): string | undefined {
  if (!source || typeof source !== 'object') {
    return undefined;
  }

  const record = source as Record<string, unknown>;

  for (const key of keys) {
    const value = record[key];

    if (
      typeof value === 'string' &&
      value.trim()
    ) {
      return value;
    }
  }

  return undefined;
}

function getBooleanValue(
  source: unknown,
  ...keys: string[]
): boolean | undefined {
  if (!source || typeof source !== 'object') {
    return undefined;
  }

  const record = source as Record<string, unknown>;

  for (const key of keys) {
    const value = record[key];

    if (typeof value === 'boolean') {
      return value;
    }
  }

  return undefined;
}

function EntityContextSkeleton() {
  return (
    <div className="space-y-5">
      <Skeleton className="h-6 w-44" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-4/5" />
      <Separator />
      <div className="grid gap-4 sm:grid-cols-2">
        <Skeleton className="h-16" />
        <Skeleton className="h-16" />
        <Skeleton className="h-16" />
        <Skeleton className="h-16" />
      </div>
    </div>
  );
}

interface LinkedEntityContextProps {
  entityType?: string | null;
  entityId?: string | null;
  entityName?: string | null;
  onOpenEntity: () => void;
}

function LinkedEntityContext({
  entityType,
  entityId,
  entityName,
  onOpenEntity,
}: LinkedEntityContextProps) {
  const normalizedType =
    entityType?.toUpperCase();

  const leadQuery = useLead(
    normalizedType === 'LEAD'
      ? entityId ?? undefined
      : undefined,
  );

  const contactQuery = useContact(
    normalizedType === 'CONTACT'
      ? entityId ?? undefined
      : undefined,
  );

  const accountQuery = useAccount(
    normalizedType === 'ACCOUNT'
      ? entityId ?? undefined
      : undefined,
  );

  // Add when useDeal exists:
  
  const dealQuery = useDeal(
    normalizedType === 'DEAL'
      ? entityId ?? undefined
      : undefined,
  );

  const activeQuery =
  normalizedType === 'LEAD'
    ? leadQuery
    : normalizedType === 'CONTACT'
      ? contactQuery
      : normalizedType === 'ACCOUNT'
        ? accountQuery
        : normalizedType === 'DEAL'
          ? dealQuery
          : null;

  const entity = activeQuery?.data;

  if (
    normalizedType === 'DEAL' &&
    !activeQuery
  ) {
    return (
      <div className="space-y-4">
        <div>
          <p className="text-sm font-semibold">
            {entityName || 'Linked deal'}
          </p>

          <p className="text-xs text-muted-foreground">
            Deal details hook is not connected on this
            page yet.
          </p>
        </div>

        <Button
          variant="outline"
          className="w-full justify-start"
          onClick={onOpenEntity}
        >
          <Link2 className="mr-2 h-4 w-4" />
          Open deal
        </Button>
      </div>
    );
  }

  if (activeQuery?.isLoading) {
    return <EntityContextSkeleton />;
  }

  if (activeQuery?.isError) {
    return (
      <Alert variant="destructive">
        <AlertTitle>
          Unable to load linked entity
        </AlertTitle>

        <AlertDescription>
          The call is linked, but the entity details
          could not be loaded.
        </AlertDescription>
      </Alert>
    );
  }

  if (!entity) {
    return (
      <div className="space-y-4">
        <div>
          <p className="font-medium">
            {entityName || 'Linked CRM record'}
          </p>

          <p className="text-sm text-muted-foreground">
            No additional entity details were returned.
          </p>
        </div>

        <Button
          variant="outline"
          className="w-full justify-start"
          onClick={onOpenEntity}
        >
          <Link2 className="mr-2 h-4 w-4" />
          Open {formatLabel(entityType)}
        </Button>
      </div>
    );
  }

  const firstName = getStringValue(
    entity,
    'firstName',
  );

  const lastName = getStringValue(
    entity,
    'lastName',
  );

  const resolvedName =
    [firstName, lastName]
      .filter(Boolean)
      .join(' ') ||
    getStringValue(
      entity,
      'name',
      'fullName',
      'accountName',
      'companyName',
      'dealName',
      'subject',
    ) ||
    entityName ||
    'Linked CRM record';

  const primaryEmail = getStringValue(
    entity,
    'email',
    'primaryEmail',
    'workEmail',
  );

  const primaryPhone = getStringValue(
    entity,
    'phone',
    'phoneNumber',
    'mobile',
    'mobileNumber',
    'primaryPhone',
  );

  const company = getStringValue(
    entity,
    'company',
    'companyName',
    'accountName',
    'organizationName',
  );

  const title = getStringValue(
    entity,
    'jobTitle',
    'title',
    'designation',
  );

  const status = getStringValue(
    entity,
    'status',
    'leadStatus',
    'contactStatus',
    'accountStatus',
  );

  const source = getStringValue(
    entity,
    'source',
    'leadSource',
  );

  const industry = getStringValue(
    entity,
    'industry',
  );

  const website = getStringValue(
    entity,
    'website',
    'websiteUrl',
  );

  const city = getStringValue(
    entity,
    'city',
    'billingCity',
    'shippingCity',
  );

  const state = getStringValue(
    entity,
    'state',
    'billingState',
    'shippingState',
  );

  const ownerName = getStringValue(
    entity,
    'ownerName',
    'assigneeName',
  );

  const converted = getBooleanValue(
    entity,
    'isConverted',
    'converted',
  );

  return (
    <div className="space-y-5">
      <div className="flex items-start gap-3">
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary/10">
          {normalizedType === 'ACCOUNT' ? (
            <Building2 className="h-5 w-5 text-primary" />
          ) : (
            <UserRound className="h-5 w-5 text-primary" />
          )}
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="truncate font-semibold">
              {resolvedName}
            </p>

            <Badge variant="secondary">
              {formatLabel(entityType)}
            </Badge>

            {converted === true && (
              <Badge
                variant="outline"
                className="border-emerald-200 bg-emerald-50 text-emerald-700"
              >
                Converted
              </Badge>
            )}
          </div>

          {title && (
            <p className="mt-1 text-sm text-muted-foreground">
              {title}
              {company ? ` at ${company}` : ''}
            </p>
          )}

          {!title && company && (
            <p className="mt-1 text-sm text-muted-foreground">
              {company}
            </p>
          )}
        </div>
      </div>

      <Separator />

      <div className="grid gap-5 sm:grid-cols-2">
        <DetailItem
          label="Phone"
          value={
            primaryPhone ? (
              <a
                href={`tel:${primaryPhone}`}
                className="inline-flex items-center gap-2 text-primary hover:underline"
              >
                <Phone className="h-4 w-4" />
                {primaryPhone}
              </a>
            ) : (
              'Not available'
            )
          }
        />

        <DetailItem
          label="Email"
          value={
            primaryEmail ? (
              <a
                href={`mailto:${primaryEmail}`}
                className="inline-flex items-center gap-2 break-all text-primary hover:underline"
              >
                <Mail className="h-4 w-4" />
                {primaryEmail}
              </a>
            ) : (
              'Not available'
            )
          }
        />

        <DetailItem
          label="Status"
          value={
            status ? (
              <Badge variant="outline">
                {formatLabel(status)}
              </Badge>
            ) : (
              'Not available'
            )
          }
        />

        <DetailItem
          label="Source"
          value={formatLabel(source)}
        />

        {industry && (
          <DetailItem
            label="Industry"
            value={industry}
          />
        )}

        {(city || state) && (
          <DetailItem
            label="Location"
            value={
              <span className="inline-flex items-center gap-2">
                <MapPin className="h-4 w-4 text-muted-foreground" />
                {[city, state]
                  .filter(Boolean)
                  .join(', ')}
              </span>
            }
          />
        )}

        {ownerName && (
          <DetailItem
            label="Record owner"
            value={ownerName}
          />
        )}

        {website && (
          <DetailItem
            label="Website"
            value={
              <a
                href={
                  website.startsWith('http')
                    ? website
                    : `https://${website}`
                }
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 text-primary hover:underline"
              >
                Visit website
                <ExternalLink className="h-3.5 w-3.5" />
              </a>
            }
          />
        )}
      </div>

      <Button
        variant="outline"
        className="w-full justify-start"
        onClick={onOpenEntity}
      >
        <Link2 className="mr-2 h-4 w-4" />
        Open full {formatLabel(entityType)} record
      </Button>
    </div>
  );
}

function ActiveCallSkeleton() {
  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <Skeleton className="h-10 w-10 rounded-md" />

          <div className="space-y-2">
            <Skeleton className="h-8 w-72" />
            <Skeleton className="h-4 w-52" />
          </div>
        </div>

        <Skeleton className="h-10 w-36" />
      </div>

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="space-y-6 xl:col-span-2">
          <Skeleton className="h-72" />
          <Skeleton className="h-72" />
        </div>

        <div className="space-y-6">
          <Skeleton className="h-96" />
          <Skeleton className="h-56" />
        </div>
      </div>
    </div>
  );
}

export default function ActiveCallPage() {
  const router = useRouter();

  const params = useParams<{
    callId?: string | string[];
  }>();

  const rawCallId = params?.callId;

  const callId =
    typeof rawCallId === 'string'
      ? rawCallId
      : rawCallId?.[0] ?? '';

  const {
    data: call,
    isLoading,
    isError,
    error,
    refetch,
  } = useCall(callId);

  if (isLoading) {
    return <ActiveCallSkeleton />;
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
          <AlertTitle>
            Unable to load active call
          </AlertTitle>

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
          <h2 className="font-semibold">
            Active call not found
          </h2>

          <p className="text-sm text-muted-foreground">
            The call may have completed, been deleted, or
            may not exist.
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
    call.entityId,
  );

  const isCompleted =
    Boolean(call.endTime) ||
    ['HELD', 'NOT_HELD', 'CANCELLED'].includes(
      call.status,
    );

  const playableRecording = isPlayableRecording(
    call.recordingUrl,
  );

  const providerStatus =
    typeof call.customData?.providerStatus ===
    'string'
      ? call.customData.providerStatus
      : null;

  const failureReason =
    typeof call.customData?.failureReason ===
    'string'
      ? call.customData.failureReason
      : null;

  const failedAt =
    typeof call.customData?.failedAt === 'string'
      ? call.customData.failedAt
      : null;

  const handleCopy = async (
    value?: string | null,
    label = 'Value',
  ) => {
    if (!value) {
      return;
    }

    try {
      await navigator.clipboard.writeText(value);
      toast.success(`${label} copied`);
    } catch {
      toast.error(`Unable to copy ${label.toLowerCase()}`);
    }
  };

  const handleOpenEntity = () => {
    if (!entityRoute) {
      toast.error(
        'No supported linked entity route is available',
      );
      return;
    }

    router.push(entityRoute);
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
                {call.entityName ||
                  call.subject ||
                  'Active Call'}
              </h1>

              <Badge
                variant="outline"
                className={getCallTypeClass(
                  call.callType,
                )}
              >
                {call.callType === 'INCOMING' ? (
                  <ArrowDownLeft className="mr-1.5 h-3.5 w-3.5" />
                ) : (
                  <ArrowUpRight className="mr-1.5 h-3.5 w-3.5" />
                )}

                {formatLabel(call.callType)}
              </Badge>

              <Badge
                variant="outline"
                className={getStatusClass(call.status)}
              >
                {formatLabel(call.status)}
              </Badge>

              {!isCompleted && (
                <Badge className="animate-pulse bg-emerald-600 text-white">
                  Live
                </Badge>
              )}
            </div>

            <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-muted-foreground">
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
                  call.durationMinutes,
                )}
              </span>

              {call.providerName && (
                <span className="inline-flex items-center gap-1.5">
                  <Server className="h-4 w-4" />
                  {call.providerName}
                </span>
              )}
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {call.phoneNumber && (
            <Button
              variant="outline"
              onClick={() =>
                handleCopy(
                  call.phoneNumber,
                  'Phone number',
                )
              }
            >
              <Copy className="mr-2 h-4 w-4" />
              Copy number
            </Button>
          )}

          {call.externalCallId && (
            <Button
              variant="outline"
              onClick={() =>
                handleCopy(
                  call.externalCallId,
                  'External call ID',
                )
              }
            >
              <Copy className="mr-2 h-4 w-4" />
              Copy external ID
            </Button>
          )}

          {entityRoute && (
            <Button onClick={handleOpenEntity}>
              <ExternalLink className="mr-2 h-4 w-4" />
              Open {formatLabel(call.entityType)}
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

      {isCompleted && (
        <Alert>
          <CheckCircle2 className="h-4 w-4" />

          <AlertTitle>Call completed</AlertTitle>

          <AlertDescription>
            CDR and completion details are available.
            Capture the disposition, notes, and next
            action before leaving this workspace.
          </AlertDescription>
        </Alert>
      )}

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="space-y-6 xl:col-span-2">
          <Card>
            <CardHeader>
              <CardTitle>Call information</CardTitle>

              <CardDescription>
                Current call state and available CDR
                information
              </CardDescription>
            </CardHeader>

            <CardContent>
              <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
                <DetailItem
                  label="Direction"
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
                      className={getStatusClass(
                        call.status,
                      )}
                    >
                      {formatLabel(call.status)}
                    </Badge>
                  }
                />

                <DetailItem
                  label="Phone number"
                  value={
                    call.phoneNumber || 'Unknown caller'
                  }
                />

                <DetailItem
                  label="Started at"
                  value={formatDateTime(
                    call.startTime,
                  )}
                />

                <DetailItem
                  label="Ended at"
                  value={formatDateTime(call.endTime)}
                />

                <DetailItem
                  label="Duration"
                  value={formatDuration(
                    call.durationSeconds,
                    call.durationMinutes,
                  )}
                />

                <DetailItem
                  label="Disposition"
                  value={
                    call.disposition || 'Not captured'
                  }
                />

                <DetailItem
                  label="Next action"
                  value={
                    call.nextAction || 'No next action'
                  }
                />

                <DetailItem
                  label="Follow-up"
                  value={formatDateTime(
                    call.followUpAt,
                  )}
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

          <CallDispositionForm
            call={call}
            onSaved={() => refetch()}
          />

          <Card>
            <CardHeader>
              <CardTitle>Call timeline</CardTitle>

              <CardDescription>
                Call progression and activity timestamps
              </CardDescription>
            </CardHeader>

            <CardContent>
              <div className="relative space-y-6 before:absolute before:bottom-2 before:left-[7px] before:top-2 before:w-px before:bg-border">
                <div className="relative flex gap-4">
                  <div className="z-10 mt-1.5 h-3.5 w-3.5 shrink-0 rounded-full border-2 border-blue-500 bg-background" />

                  <DetailItem
                    label="Call initiated"
                    value={formatDateTime(
                      call.createdAt,
                    )}
                  />
                </div>

                <div className="relative flex gap-4">
                  <div className="z-10 mt-1.5 h-3.5 w-3.5 shrink-0 rounded-full border-2 border-emerald-500 bg-background" />

                  <DetailItem
                    label="Call connected"
                    value={formatDateTime(
                      call.startTime,
                    )}
                  />
                </div>

                <div className="relative flex gap-4">
                  <div
                    className={[
                      'z-10',
                      'mt-1.5',
                      'h-3.5',
                      'w-3.5',
                      'shrink-0',
                      'rounded-full',
                      'border-2',
                      isCompleted
                        ? 'border-violet-500'
                        : 'border-muted-foreground',
                      'bg-background',
                    ].join(' ')}
                  />

                  <DetailItem
                    label="Call completed"
                    value={formatDateTime(
                      call.endTime,
                    )}
                  />
                </div>

                <div className="relative flex gap-4">
                  <div className="z-10 mt-1.5 h-3.5 w-3.5 shrink-0 rounded-full border-2 border-amber-500 bg-background" />

                  <DetailItem
                    label="Follow-up scheduled"
                    value={formatDateTime(
                      call.followUpAt,
                    )}
                  />
                </div>

                <div className="relative flex gap-4">
                  <div className="z-10 mt-1.5 h-3.5 w-3.5 shrink-0 rounded-full border-2 border-slate-500 bg-background" />

                  <DetailItem
                    label="Last updated"
                    value={formatDateTime(
                      call.updatedAt,
                    )}
                  />
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>
                {call.entityType
                  ? `${formatLabel(
                      call.entityType,
                    )} context`
                  : 'Caller context'}
              </CardTitle>

              <CardDescription>
                Important CRM information for handling
                this call
              </CardDescription>
            </CardHeader>

            <CardContent>
              {call.entityId && call.entityType ? (
                <LinkedEntityContext
                  entityType={call.entityType}
                  entityId={call.entityId}
                  entityName={call.entityName}
                  onOpenEntity={handleOpenEntity}
                />
              ) : (
                <UnknownCallerWorkflow
                  callId={call.id}
                  phone={call.phoneNumber}
                />
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Recording</CardTitle>

              <CardDescription>
                Provider recording and playback
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
                        {call.externalCallId ||
                          'Provider recording'}
                      </p>
                    </div>
                  </div>

                  <audio
                    controls
                    preload="metadata"
                    className="w-full"
                    src={
                      call.recordingUrl ?? undefined
                    }
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
                    The recording may become available
                    after the provider sends the CDR.
                  </p>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>
                Provider information
              </CardTitle>

              <CardDescription>
                Telephony integration metadata
              </CardDescription>
            </CardHeader>

            <CardContent className="space-y-5">
              <DetailItem
                label="Provider"
                value={
                  call.providerName ||
                  'Not available'
                }
              />

              <DetailItem
                label="External call ID"
                value={
                  call.externalCallId ? (
                    <button
                      type="button"
                      className="inline-flex max-w-full items-center gap-2 text-left text-primary hover:underline"
                      onClick={() =>
                        handleCopy(
                          call.externalCallId,
                          'External call ID',
                        )
                      }
                    >
                      <span className="truncate">
                        {call.externalCallId}
                      </span>

                      <Copy className="h-3.5 w-3.5 shrink-0" />
                    </button>
                  ) : (
                    'Not available'
                  )
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

              <Separator />

              <DetailItem
                label="Owner"
                value={
                  call.ownerUserId ||
                  'No owner assigned'
                }
              />

              <DetailItem
                label="Assigned to"
                value={
                  call.assigneeName ||
                  call.assignedTo ||
                  'Unassigned'
                }
              />

              <DetailItem
                label="Created by"
                value={call.createdBy || 'System'}
              />

              <DetailItem
                label="Created at"
                value={formatDateTime(
                  call.createdAt,
                )}
              />
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}