"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, RefreshCw } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { acquisitionKeys, useLeadIngestionEvents } from "@/lib/hooks/acquisition";
import { usePermissions } from "@/lib/hooks/usePermissions";
import {
  LeadIngestionEventStatus,
  LeadIngestionFailureStage,
  leadIngestionEventStatuses,
} from "@/types/acquisition";

const statusLabels: Record<LeadIngestionEventStatus, string> = {
  RECEIVED: "Received",
  PROCESSING: "Processing",
  PROCESSED: "Lead created",
  REJECTED: "Needs fix",
  DUPLICATE: "Already existed",
  FAILED: "Failed",
};

const stageLabels: Record<LeadIngestionFailureStage, string> = {
  MAPPING: "Mapping",
  VALIDATION: "Check",
  DEDUPLICATION: "Duplicate check",
  LEAD_CREATION: "Lead creation",
  UNKNOWN: "Unknown",
};

const PAGE_SIZE = 20;

export default function AcquisitionEventsPage() {
  const params = useParams<{ configId: string }>();
  const configId = params?.configId ?? "";

  const queryClient = useQueryClient();
  const { canViewAcquisition } = usePermissions();

  const [statusFilter, setStatusFilter] = useState<LeadIngestionEventStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);

  const queryParams = {
    ...(statusFilter !== "ALL" ? { status: statusFilter } : {}),
    page,
    size: PAGE_SIZE,
  };

  const eventsQuery = useLeadIngestionEvents(configId, queryParams);

  if (!canViewAcquisition) {
    return (
      <div className="space-y-6 p-6">
        <h1 className="text-2xl font-semibold">Ingestion Events</h1>
        <p className="text-sm text-muted-foreground">
          You do not have permission to view ingestion events.
        </p>
      </div>
    );
  }

  const events = eventsQuery.data?.data ?? [];
  const meta = eventsQuery.data?.meta;
  const isFiltered = statusFilter !== "ALL";

  const handleStatusChange = (value: string) => {
    setStatusFilter(value as LeadIngestionEventStatus | "ALL");
    setPage(0);
  };

  const handleRefresh = () => {
    queryClient.invalidateQueries({ queryKey: acquisitionKeys.events(configId) });
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <div className="mb-2 flex items-center gap-1 text-sm text-muted-foreground">
            <Link href="/acquisition" className="hover:text-foreground">
              Acquisition
            </Link>
            <span>·</span>
            <Link href={`/acquisition/configs/${configId}`} className="hover:text-foreground inline-flex items-center gap-1">
              <ArrowLeft className="h-3 w-3" /> Source
            </Link>
          </div>
          <h1 className="text-2xl font-semibold">Lead History</h1>
          <p className="text-sm text-muted-foreground">
            See which leads were received, which were created, and which need attention.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Select value={statusFilter} onValueChange={handleStatusChange}>
            <SelectTrigger className="w-[160px]">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All</SelectItem>
              {leadIngestionEventStatuses.map((status) => (
                <SelectItem key={status} value={status}>
                  {statusLabels[status]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button variant="outline" size="sm" onClick={handleRefresh}>
            <RefreshCw className="mr-2 h-4 w-4" /> Refresh
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Events</CardTitle>
        </CardHeader>
        <CardContent>
          {eventsQuery.isLoading ? (
            <p className="text-sm text-muted-foreground">Loading events…</p>
          ) : eventsQuery.isError ? (
            <div className="space-y-2">
              <p className="text-sm text-muted-foreground">
                Failed to load ingestion events.
              </p>
              <Button variant="outline" size="sm" onClick={() => eventsQuery.refetch()}>
                Retry
              </Button>
            </div>
          ) : events.length === 0 ? (
            isFiltered ? (
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground">
                  No events match the selected status.
                </p>
                <Button variant="outline" size="sm" onClick={() => handleStatusChange("ALL")}>
                  Show all statuses
                </Button>
              </div>
            ) : (
              <div className="space-y-1">
                <p className="text-sm text-muted-foreground">No ingestion events yet.</p>
                <p className="text-xs text-muted-foreground">
                  Events will appear here when this configuration receives inbound
                  Lead data.
                </p>
              </div>
            )
          ) : (
            <div className="space-y-3">
              {events.map((event) => (
                <Link
                  key={event.id}
                  href={`/acquisition/configs/${configId}/events/${event.id}`}
                  className="block rounded-lg border p-4 transition-colors hover:bg-muted/40"
                >
                  <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <EventStatusBadge status={event.status} />
                        {event.failureStage && (
                          <Badge variant="outline" className="text-xs">
                            {stageLabels[event.failureStage] ?? event.failureStage}
                          </Badge>
                        )}
                        {event.attemptCount != null && event.attemptCount > 1 && (
                          <Badge variant="outline" className="text-xs">
                            #{event.attemptCount}
                          </Badge>
                        )}
                        <span className="truncate text-sm font-medium">
                          {event.externalEventId ?? event.id}
                        </span>
                      </div>
                      <p className="mt-1 truncate text-xs text-muted-foreground">
                        Received{" "}
                        {event.receivedAt ? new Date(event.receivedAt).toLocaleString() : "—"}
                        {event.processedAt &&
                          ` · Processed ${new Date(event.processedAt).toLocaleString()}`}
                      </p>
                    </div>

                    <div className="min-w-0 md:text-right">
                      <p className="truncate text-xs text-muted-foreground">
                        {event.leadId ? `Lead ${event.leadId}` : "No lead"}
                      </p>
                      {event.errorCode && (
                        <p className="truncate text-xs text-red-500">
                          {event.errorCode}
                          {event.failureStage ? ` · ${stageLabels[event.failureStage] ?? event.failureStage}` : ""}
                        </p>
                      )}
                    </div>
                  </div>
                </Link>
              ))}
            </div>
          )}

          {meta && meta.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between border-t pt-4">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                disabled={page === 0}
              >
                Previous
              </Button>
              <span className="text-sm text-muted-foreground">
                Page {meta.page + 1} of {meta.totalPages} · {meta.total} events
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() =>
                  setPage((current) => Math.min(meta.totalPages - 1, current + 1))
                }
                disabled={page >= meta.totalPages - 1}
              >
                Next
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function EventStatusBadge({ status }: { status: LeadIngestionEventStatus }) {
  const variant =
    status === "PROCESSED"
      ? "default"
      : status === "DUPLICATE"
        ? "secondary"
        : status === "REJECTED" || status === "FAILED"
          ? "destructive"
          : "secondary";

  return <Badge variant={variant}>{statusLabels[status] ?? status}</Badge>;
}
