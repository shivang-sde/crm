"use client";

import * as React from "react";

import { AlertTriangle, RefreshCw, ServerCrash } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Skeleton } from "@/components/ui/skeleton";
import { formatTimestamp, RecoveryConfirm } from "@/components/operations/RecoveryConfirm";
import { useDeadLetters, useRedriveDeadLetter } from "@/lib/hooks/operations";
import type { DeadLetterView } from "@/types/operations";

/**
 * OPS-01: RabbitMQ dead letter inspection and controlled re-drive.
 *
 * Every row is a message that a bounded retry cycle could not deliver. Reading this
 * list never consumes, re-publishes or removes anything: the backend uses the
 * RabbitMQ management API's requeue acknowledgement mode, so inspection is
 * non-destructive. It does rotate the queue head, which is why the page is a manual
 * refresh rather than a polling view.
 */
export function DeadLetterQueueTable() {
  const [limit] = React.useState(20);
  const { data, isLoading, isError, error, refetch, isFetching } = useDeadLetters(limit);
  const redrive = useRedriveDeadLetter();
  const [target, setTarget] = React.useState<DeadLetterView | null>(null);

  const messages = data?.data ?? [];

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
      <Alert variant="destructive">
        <ServerCrash className="h-4 w-4" />
        <AlertTitle>Dead letter inspection is unavailable</AlertTitle>
        <AlertDescription className="space-y-2">
          <p>
            {error instanceof Error ? error.message : "The DLQ could not be inspected."}
          </p>
          <p>
            Inspection needs the RabbitMQ management API
            (<span className="font-mono">app.operator-tools.dlq.management-url</span>). This page
            will not fall back to consuming the queue, so nothing can be lost by viewing it.
          </p>
        </AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <p className="text-sm text-muted-foreground">
          {messages.length === 0
            ? "No dead letters. Messages appear here only after the bounded consumer retry is exhausted."
            : `${messages.length} dead letter${messages.length === 1 ? "" : "s"}`}
        </p>
        <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching}>
          <RefreshCw className="mr-2 h-4 w-4" />
          Refresh
        </Button>
      </div>

      {messages.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-foreground">
          The dead letter queue is empty.
        </p>
      ) : (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Message</TableHead>
                <TableHead>Event</TableHead>
                <TableHead>Original destination</TableHead>
                <TableHead>Deaths</TableHead>
                <TableHead>Tenant</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {messages.map((message, index) => (
                <TableRow key={message.messageId ?? index}>
                  <TableCell className="font-mono text-xs">
                    <div className="max-w-[16rem] truncate" title={message.messageId ?? undefined}>
                      {message.messageId ?? "(no message id)"}
                    </div>
                    {message.redriveOf && (
                      <div className="text-[10px] text-muted-foreground">
                        re-drive of {message.redriveOf}
                      </div>
                    )}
                    {message.correlationId === null && (
                      <div className="text-[10px] text-muted-foreground">
                        publisher sets no correlation id
                      </div>
                    )}
                  </TableCell>
                  <TableCell className="text-xs">
                    {message.entityType ? (
                      <>
                        <span className="font-medium">{message.entityType}</span>
                        <span className="text-muted-foreground">.{message.eventType}</span>
                      </>
                    ) : (
                      <span className="text-muted-foreground">not a CRM event</span>
                    )}
                  </TableCell>
                  <TableCell className="font-mono text-xs">
                    <div>{message.originalExchange ?? message.exchange}</div>
                    <div className="text-muted-foreground">
                      {message.originalRoutingKey ?? message.routingKey}
                    </div>
                    <div className="text-[10px] text-muted-foreground">
                      {message.deathReason ?? "unknown reason"} ·{" "}
                      {formatTimestamp(message.timestamp)}
                    </div>
                  </TableCell>
                  <TableCell className="text-xs">
                    {message.deathCount ?? "—"}
                    {message.redelivered && (
                      <Badge variant="outline" className="ml-2 text-[10px]">
                        redelivered
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell className="font-mono text-[10px]">
                    {message.tenantId ? (
                      message.tenantId.slice(0, 8)
                    ) : (
                      <span className="text-muted-foreground">platform</span>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    {message.redriveable ? (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => setTarget(message)}
                        disabled={redrive.isPending}
                      >
                        Re-drive
                      </Button>
                    ) : (
                      <span
                        className="text-xs text-muted-foreground"
                        title={
                          message.replayable
                            ? "Destination exchange is not in the approved re-drive topology."
                            : "A successful re-drive is already recorded for this message id."
                        }
                      >
                        {message.replayable ? "Not permitted" : "Already re-driven"}
                      </span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <RecoveryConfirm
        open={target !== null}
        onOpenChange={(open) => {
          if (!open) setTarget(null);
        }}
        title="Re-drive this dead letter?"
        confirmLabel="Re-drive message"
        description={
          <>
            <p>
              The message will be republished to{" "}
              <span className="font-mono">
                {target?.originalExchange ?? target?.exchange} /{" "}
                {target?.originalRoutingKey ?? target?.routingKey}
              </span>
              , the destination RabbitMQ recorded when it dead-lettered.
            </p>
            <p className="flex items-start gap-2">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-500" />
              <span>
                The downstream workflow may execute again. The body is republished unchanged, so the
                canonical event id is preserved and the consumer&apos;s duplicate-execution guard makes
                this a no-op if the trigger already ran. If the original failure happened before that
                record was created, it will run this time.
              </span>
            </p>
            <p>
              The original message stays in the dead letter queue. A successful re-drive is recorded
              and that message can no longer be re-driven again without an explicit audit-record
              deletion, which is what stops a re-drive loop.
            </p>
          </>
        }
        onConfirm={async () => {
          if (!target?.messageId) throw new Error("Message has no id");
          await redrive.mutateAsync(target.messageId);
          setTarget(null);
        }}
      />
    </div>
  );
}
