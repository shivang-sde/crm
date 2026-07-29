'use client';

import React from 'react';
import {
  CalendarClock,
  CheckCircle2,
  FileText,
  ListTodo,
} from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';

import { useSaveCallDisposition } from '@/lib/hooks/calls';
import { usePermissions } from '@/lib/hooks/usePermissions';

import type {
  CallDispositionRequest,
  CallResponse,
} from '@/types/calls';

interface CallDispositionFormProps {
  call: CallResponse;
  onSaved?: () => void;
}

const dispositionOptions = [
  { value: 'CONNECTED', label: 'Connected' },
  { value: 'NOT_CONNECTED', label: 'Not Connected' },
  { value: 'INTERESTED', label: 'Interested' },
  { value: 'NOT_INTERESTED', label: 'Not Interested' },
  { value: 'CALLBACK_REQUESTED', label: 'Callback Requested' },
  { value: 'WRONG_NUMBER', label: 'Wrong Number' },
  { value: 'NO_ANSWER', label: 'No Answer' },
  { value: 'BUSY', label: 'Busy' },
  { value: 'VOICEMAIL', label: 'Voicemail' },
  { value: 'FOLLOW_UP_REQUIRED', label: 'Follow-up Required' },
];

const nextActionOptions = [
  { value: 'NO_ACTION', label: 'No Action' },
  { value: 'CALL_BACK', label: 'Call Back' },
  { value: 'FOLLOW_UP', label: 'Follow Up' },
  { value: 'SEND_EMAIL', label: 'Send Email' },
  { value: 'SEND_PROPOSAL', label: 'Send Proposal' },
  { value: 'SCHEDULE_MEETING', label: 'Schedule Meeting' },
  { value: 'CREATE_TASK', label: 'Create Task' },
  { value: 'UPDATE_RECORD', label: 'Update CRM Record' },
  { value: 'CLOSE_RECORD', label: 'Close Record' },
];

function toDateTimeLocal(value?: string | null): string {
  if (!value) {
    return '';
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return '';
  }

  const timezoneOffset = date.getTimezoneOffset() * 60_000;

  return new Date(date.getTime() - timezoneOffset)
    .toISOString()
    .slice(0, 16);
}

function formatLabel(value?: string | null): string {
  if (!value) {
    return 'Not set';
  }

  return value
    .toLowerCase()
    .split('_')
    .map(
      (part) =>
        part.charAt(0).toUpperCase() + part.slice(1),
    )
    .join(' ');
}

export function CallDispositionForm({
  call,
  onSaved,
}: CallDispositionFormProps) {
  const [disposition, setDisposition] = React.useState(
    call.disposition ?? '',
  );

  const [notes, setNotes] = React.useState(
    call.notes ?? '',
  );

  const [nextAction, setNextAction] = React.useState(
    call.nextAction ?? '',
  );

  const [followUpAt, setFollowUpAt] = React.useState(
    toDateTimeLocal(call.followUpAt),
  );

  const saveDisposition = useSaveCallDisposition();
  const { canEditCalls } = usePermissions();

  const isCompletedCall =
    call.status === 'HELD' ||
    call.status === 'NOT_HELD' ||
    call.status === 'CANCELLED' ||
    Boolean(call.endTime);

  React.useEffect(() => {
    setDisposition(call.disposition ?? '');
    setNotes(call.notes ?? '');
    setNextAction(call.nextAction ?? '');
    setFollowUpAt(toDateTimeLocal(call.followUpAt));
  }, [
    call.disposition,
    call.notes,
    call.nextAction,
    call.followUpAt,
  ]);

  const requiresFollowUpDate = [
    'CALL_BACK',
    'FOLLOW_UP',
    'SEND_PROPOSAL',
    'SCHEDULE_MEETING',
  ].includes(nextAction);

  async function handleSubmit(
    event: React.FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    if (!canEditCalls) {
      toast.error(
        'You do not have permission to update this call',
      );
      return;
    }

    if (!isCompletedCall) {
      toast.error(
        'Disposition can only be saved after the call ends',
      );
      return;
    }

    if (!disposition) {
      toast.error(
        'Select a disposition before saving',
      );
      return;
    }

    if (requiresFollowUpDate && !followUpAt) {
      toast.error(
        'Select a follow-up date for this next action',
      );
      return;
    }

    const request: CallDispositionRequest = {
      disposition,
      notes: notes.trim() || undefined,
      nextAction: nextAction || undefined,
      followUpAt: followUpAt
        ? new Date(followUpAt).toISOString()
        : undefined,
    };

    try {
      await saveDisposition.mutateAsync({
        id: call.id,
        request,
      });

      toast.success('Call outcome saved');
      onSaved?.();
    } catch (error) {
      console.error(
        'Failed to save call disposition:',
        error,
      );

      toast.error(
        error instanceof Error
          ? error.message
          : 'Failed to save call outcome',
      );
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-4">
          <div>
            <CardTitle>Agent Call Outcome</CardTitle>

            <CardDescription>
              Capture the result, notes, next action,
              and required follow-up.
            </CardDescription>
          </div>

          {call.disposition && (
            <CheckCircle2 className="h-5 w-5 text-emerald-600" />
          )}
        </div>
      </CardHeader>

      <CardContent>
        {!isCompletedCall ? (
          <div className="rounded-md border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-300">
            Disposition and follow-up information can be
            saved after the call completes.
          </div>
        ) : (
          <form
            className="space-y-6"
            onSubmit={handleSubmit}
          >
            <div className="grid gap-5 md:grid-cols-2">
              <div className="space-y-2">
                <label className="text-sm font-medium">
                  Disposition *
                </label>

                <Select
                  value={disposition}
                  onValueChange={setDisposition}
                  disabled={!canEditCalls}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select disposition" />
                  </SelectTrigger>

                  <SelectContent>
                    {dispositionOptions.map((option) => (
                      <SelectItem
                        key={option.value}
                        value={option.value}
                      >
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <label className="flex items-center gap-2 text-sm font-medium">
                  <ListTodo className="h-4 w-4 text-muted-foreground" />
                  Next Action
                </label>

                <Select
                  value={nextAction}
                  onValueChange={setNextAction}
                  disabled={!canEditCalls}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select next action" />
                  </SelectTrigger>

                  <SelectContent>
                    {nextActionOptions.map((option) => (
                      <SelectItem
                        key={option.value}
                        value={option.value}
                      >
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-2">
              <label className="flex items-center gap-2 text-sm font-medium">
                <CalendarClock className="h-4 w-4 text-muted-foreground" />
                Follow-up Date and Time
                {requiresFollowUpDate && (
                  <span className="text-destructive">
                    *
                  </span>
                )}
              </label>

              <Input
                type="datetime-local"
                value={followUpAt}
                onChange={(event) =>
                  setFollowUpAt(event.target.value)
                }
                disabled={!canEditCalls}
              />

              <p className="text-xs text-muted-foreground">
                Required for callbacks, follow-ups,
                proposals, and scheduled meetings.
              </p>
            </div>

            <div className="space-y-2">
              <label className="flex items-center gap-2 text-sm font-medium">
                <FileText className="h-4 w-4 text-muted-foreground" />
                Notes
              </label>

              <Textarea
                rows={5}
                value={notes}
                onChange={(event) =>
                  setNotes(event.target.value)
                }
                placeholder="Add call outcome, customer requirements, objections, commitments, and follow-up context..."
                disabled={!canEditCalls}
              />
            </div>

            {(call.disposition ||
              call.nextAction ||
              call.followUpAt) && (
              <div className="grid gap-4 rounded-md border bg-muted/30 p-4 sm:grid-cols-3">
                <div>
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                    Saved disposition
                  </p>

                  <p className="mt-1 text-sm font-medium">
                    {formatLabel(call.disposition)}
                  </p>
                </div>

                <div>
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                    Saved next action
                  </p>

                  <p className="mt-1 text-sm font-medium">
                    {formatLabel(call.nextAction)}
                  </p>
                </div>

                <div>
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                    Saved follow-up
                  </p>

                  <p className="mt-1 text-sm font-medium">
                    {call.followUpAt
                      ? new Date(
                          call.followUpAt,
                        ).toLocaleString()
                      : 'Not scheduled'}
                  </p>
                </div>
              </div>
            )}

            <div className="flex justify-end">
              <Button
                type="submit"
                disabled={
                  !canEditCalls ||
                  saveDisposition.isPending
                }
              >
                {saveDisposition.isPending
                  ? 'Saving...'
                  : call.disposition
                    ? 'Update Call Outcome'
                    : 'Save Call Outcome'}
              </Button>
            </div>
          </form>
        )}
      </CardContent>
    </Card>
  );
}

export default CallDispositionForm;