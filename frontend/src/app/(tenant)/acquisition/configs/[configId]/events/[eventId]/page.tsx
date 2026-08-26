"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, RefreshCw } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  acquisitionKeys,
  useLeadIngestionEvent,
} from "@/lib/hooks/acquisition";
import { usePermissions } from "@/lib/hooks/usePermissions";

export default function AcquisitionEventDetailPage() {
  const params = useParams<{ configId: string; eventId: string }>();
  const configId = params?.configId ?? "";
  const eventId = params?.eventId ?? "";

  const queryClient = useQueryClient();
  const { canViewAcquisition } = usePermissions();

  const eventQuery = useLeadIngestionEvent(configId, eventId);

  if (!canViewAcquisition) {
    return (
      <div className="space-y-6 p-6">
        <h1 className="text-2xl font-semibold">Ingestion Event</h1>
        <p className="text-sm text-muted-foreground">
          You do not have permission to view ingestion events.
        </p>
      </div>
    );
  }

  const event = eventQuery.data;

  const handleRefresh = () => {
    queryClient.invalidateQueries({
      queryKey: acquisitionKeys.eventDetail(configId, eventId),
    });
  };

  return (
    <div className="space-y-6 p-6">
      <div>
        <Link
          href={`/acquisition/configs/${configId}/events`}
          className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" /> Back to events
        </Link>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold">Ingestion Event</h1>
          {event && (
            <Badge
              variant={
                event.status === "PROCESSED"
                  ? "default"
                  : event.status === "REJECTED" || event.status === "FAILED"
                    ? "destructive"
                    : "secondary"
              }
            >
              {event.status}
            </Badge>
          )}
          <Button variant="outline" size="sm" onClick={handleRefresh}>
            <RefreshCw className="mr-2 h-4 w-4" /> Refresh
          </Button>
        </div>
      </div>

      {eventQuery.isLoading ? (
        <p className="text-sm text-muted-foreground">Loading event…</p>
      ) : eventQuery.isError || !event ? (
        <Card>
          <CardContent className="space-y-2 pt-6">
            <p className="text-sm text-muted-foreground">
              This ingestion event could not be found.
            </p>
            <Button variant="outline" size="sm" onClick={() => eventQuery.refetch()}>
              Retry
            </Button>
          </CardContent>
        </Card>
      ) : (
        <>
          <Card>
            <CardHeader>
              <CardTitle>Overview</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-x-8 gap-y-3 text-sm md:grid-cols-2">
              <DetailRow label="Received At">
                {event.receivedAt ? new Date(event.receivedAt).toLocaleString() : "—"}
              </DetailRow>
              <DetailRow label="Processed At">
                {event.processedAt ? new Date(event.processedAt).toLocaleString() : "—"}
              </DetailRow>
              <DetailRow label="External Event ID">
                <span className="break-all">{event.externalEventId ?? "—"}</span>
              </DetailRow>
              <DetailRow label="Idempotency Key">
                <span className="break-all">{event.idempotencyKey ?? "—"}</span>
              </DetailRow>
              <DetailRow label="Lead ID">
                <span className="break-all">{event.leadId ?? "—"}</span>
              </DetailRow>
              <DetailRow label="Created / Updated">
                {`${new Date(event.createdAt).toLocaleString()} · ${new Date(event.updatedAt).toLocaleString()}`}
              </DetailRow>
            </CardContent>
          </Card>

          {(event.status === "PROCESSED" ||
            event.status === "REJECTED" ||
            event.status === "FAILED") && (
            <Card>
              <CardHeader>
                <CardTitle>Processing Result</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                {event.status === "PROCESSED" && (
                  <p className="text-muted-foreground">
                    Successfully processed.
                  </p>
                )}

                {event.status === "REJECTED" && (
                  <div className="space-y-1">
                    <p className="font-medium">Rejected</p>
                    <p className="text-muted-foreground">
                      The event was received but could not be accepted for
                      processing.
                    </p>
                  </div>
                )}

                {event.status === "FAILED" && (
                  <div className="space-y-1">
                    <p className="font-medium">Failed</p>
                    <p className="text-muted-foreground">
                      The event was accepted but processing failed.
                    </p>
                  </div>
                )}

                {(event.errorCode || event.errorMessage) && (
                  <div className="rounded-md border border-red-200 bg-red-50 p-3 dark:border-red-900 dark:bg-red-950/40">
                    {event.errorCode && (
                      <p className="font-medium text-red-600 dark:text-red-400">
                        {event.errorCode}
                      </p>
                    )}
                    {event.errorMessage && (
                      <p className="break-words text-red-600 dark:text-red-400">
                        {event.errorMessage}
                      </p>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader>
              <CardTitle>Raw Payload</CardTitle>
            </CardHeader>
            <CardContent>
              <JsonBlock value={event.rawPayload} />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Headers</CardTitle>
            </CardHeader>
            <CardContent>
              <JsonBlock value={event.headers} />
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}

function DetailRow({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </p>
      <div>{children}</div>
    </div>
  );
}

function JsonBlock({ value }: { value?: Record<string, unknown> | null }) {
  if (!value || Object.keys(value).length === 0) {
    return <p className="text-sm text-muted-foreground">No data stored.</p>;
  }

  return (
    <pre className="max-h-96 overflow-auto whitespace-pre-wrap break-words rounded-md border bg-muted/40 p-4 text-xs leading-relaxed">
      {JSON.stringify(value, null, 2)}
    </pre>
  );
}
