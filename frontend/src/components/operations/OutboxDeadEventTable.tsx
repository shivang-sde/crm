"use client";

import * as React from "react";

import { AlertTriangle, ChevronLeft, ChevronRight, RefreshCw } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { formatTimestamp, RecoveryConfirm } from "@/components/operations/RecoveryConfirm";
import { useDeadOutboxEvents, useRequeueDeadOutboxEvent } from "@/lib/hooks/operations";
import type { OutboxDeadEventView } from "@/types/operations";

const PAGE_SIZE = 20;

/**
 * OPS-02: terminal (DEAD) outbox events and controlled requeue.
 *
 * A DEAD event is one whose publish attempt budget was exhausted. Nothing replays
 * it automatically: the normal outbox publisher only selects PENDING and
 * PROCESSING rows, so a DEAD event stays parked until an operator acts here.
 */
export function OutboxDeadEventTable() {
  const [page, setPage] = React.useState(0);
  const { data, isLoading, isError, error, refetch, isFetching } = useDeadOutboxEvents(page, PAGE_SIZE);
  const requeue = useRequeueDeadOutboxEvent();
  const [target, setTarget] = React.useState<OutboxDeadEventView | null>(null);

  const events = data?.data ?? [];
  const meta = (data?.meta ?? {}) as { total?: number; totalPages?: number };
  const totalPages = meta.totalPages ?? 0;

  if (isLoading) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-10 w-full" />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="rounded-md border p-4 text-sm text-destructive">
        {error instanceof Error ? error.message : "Terminal outbox events could not be loaded."}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <p className="text-sm text-muted-foreground">
          {events.length === 0
            ? "No terminal outbox events. An event appears here only after the publish attempt budget is exhausted."
            : `Showing ${page * PAGE_SIZE + 1}-${page * PAGE_SIZE + events.length}${
                meta.total ? ` of ${meta.total}` : ""
              }`}
        </p>
        <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching}>
          <RefreshCw className="mr-2 h-4 w-4" />
          Refresh
        </Button>
      </div>

      {events.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-foreground">
          Nothing is stuck. Every outbox event has been published.
        </p>
      ) : (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Event</TableHead>
                <TableHead>Entity</TableHead>
                <TableHead>Attempts</TableHead>
                <TableHead>Failed at</TableHead>
                <TableHead>Last error</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {events.map((event) => (
                <TableRow key={event.eventId}>
                  <TableCell className="text-xs">
                    <div className="font-medium">{event.eventName}</div>
                    <div className="font-mono text-[10px] text-muted-foreground">
                      {event.eventId}
                    </div>
                  </TableCell>
                  <TableCell className="text-xs">
                    <div>{event.entityType}</div>
                    <div className="font-mono text-[10px] text-muted-foreground">
                      {event.entityId}
                    </div>
                    <div className="text-[10px] text-muted-foreground">
                      {event.tenantId ? `tenant ${event.tenantId.slice(0, 8)}` : "platform"}
                    </div>
                  </TableCell>
                  <TableCell className="text-xs">
                    {event.attempts ?? "—"}
                    {event.payloadAvailable && (
                      <Badge variant="outline" className="ml-2 text-[10px]">
                        payload stored
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-xs">{formatTimestamp(event.deadAt)}</TableCell>
                  <TableCell className="max-w-[24rem] text-xs">
                    <div className="truncate font-mono" title={event.lastError ?? undefined}>
                      {event.lastError ?? "—"}
                    </div>
                  </TableCell>
                  <TableCell className="text-right">
                    {event.requeueable ? (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => setTarget(event)}
                        disabled={requeue.isPending}
                      >
                        Requeue
                      </Button>
                    ) : (
                      <span className="text-xs text-muted-foreground">{event.status}</span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-end gap-2">
          <Button
            size="sm"
            variant="outline"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0 || isFetching}
          >
            <ChevronLeft className="mr-1 h-4 w-4" />
            Previous
          </Button>
          <span className="text-xs text-muted-foreground">
            Page {page + 1} of {totalPages}
          </span>
          <Button
            size="sm"
            variant="outline"
            onClick={() => setPage((p) => p + 1)}
            disabled={page + 1 >= totalPages || isFetching}
          >
            Next
            <ChevronRight className="ml-1 h-4 w-4" />
          </Button>
        </div>
      )}

      <RecoveryConfirm
        open={target !== null}
        onOpenChange={(open) => {
          if (!open) setTarget(null);
        }}
        title="Requeue this terminal outbox event?"
        confirmLabel="Requeue event"
        description={
          <>
            <p>
              The event returns to the retryable state and the normal outbox publisher will process it
              again on its next poll. This does not publish inline and does not start a separate retry
              loop.
            </p>
            <p className="flex items-start gap-2">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-500" />
              <span>
                The downstream consumer may see the event again. Its attempt count, last error and
                dead-at timestamp are preserved, so you can still see that it failed before. If the
                cause has not been fixed, it will fail again and can exhaust its budget a second time.
              </span>
            </p>
          </>
        }
        onConfirm={async () => {
          if (!target) throw new Error("No event selected");
          await requeue.mutateAsync(target.eventId);
          setTarget(null);
        }}
      />
    </div>
  );
}
