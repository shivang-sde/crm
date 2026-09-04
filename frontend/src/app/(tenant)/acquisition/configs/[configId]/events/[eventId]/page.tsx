"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import {
  ArrowLeft,
  RefreshCw,
  CheckCircle2,
  XCircle,
  Circle,
  AlertTriangle,
  Copy,
  ExternalLink,
} from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  acquisitionKeys,
  useLeadIngestionEvent,
  useReprocessLeadIngestionEvent,
} from "@/lib/hooks/acquisition";
import { usePermissions } from "@/lib/hooks/usePermissions";
import type {
  LeadIngestionEventDetailResponse,
  LeadIngestionFailureStage,
} from "@/types/acquisition";

const stageLabels: Record<LeadIngestionFailureStage, string> = {
  MAPPING: "Mapping",
  VALIDATION: "Validation",
  DEDUPLICATION: "Deduplication",
  LEAD_CREATION: "Lead Creation",
  UNKNOWN: "Unknown",
};

const stageGuidance: Record<
  LeadIngestionFailureStage,
  { title: string; desc: string; fix: string }
> = {
  MAPPING: {
    title: "Mapping failed",
    desc: "A required CRM field could not be mapped from the incoming payload.",
    fix: "Check your field mappings — ensure the source path exists in the payload and the target field is configured. Open Mapping to fix.",
  },
  VALIDATION: {
    title: "Validation failed",
    desc: "The mapped data did not pass CRM validation.",
    fix: "Fix the mapping or ensure the payload contains required fields (e.g., firstName, valid email). Open Mapping to adjust.",
  },
  DEDUPLICATION: {
    title: "Duplicate detected",
    desc: "A lead with this email or phone already exists.",
    fix: "No action needed — the existing lead was linked. View the lead to continue.",
  },
  LEAD_CREATION: {
    title: "Lead creation failed",
    desc: "The lead could not be created despite valid data.",
    fix: "Check lead status/source configuration and required custom fields, then reprocess.",
  },
  UNKNOWN: {
    title: "Processing failed",
    desc: "An unexpected error occurred.",
    fix: "Retry the operation. If it persists, check logs and ensure required status is configured.",
  },
};

function getTimeline(event: LeadIngestionEventDetailResponse) {
  const isProcessed = event.status === "PROCESSED";
  const isDuplicate = event.status === "DUPLICATE";
  const isRejected = event.status === "REJECTED";
  const isFailed = event.status === "FAILED";
  const isProcessing = event.status === "PROCESSING";
  const isReceived = event.status === "RECEIVED";
  const stage = event.failureStage;

  // Conservative: only mark steps as done if we have evidence they passed.
  // MAPPING step: fails only if stage === MAPPING
  // VALIDATION step: fails only if stage === VALIDATION
  // DEDUPLICATION: stage === DEDUPLICATION means duplicate
  // LEAD step: succeeds only if PROCESSED
  const mappingState =
    stage === "MAPPING" ? "failed" : isReceived ? "pending" : "done";
  const validationState =
    stage === "VALIDATION"
      ? "failed"
      : stage === "MAPPING"
        ? "pending"
        : isReceived
          ? "pending"
          : "done";
  const dedupState = isDuplicate
    ? "duplicate"
    : stage === "DEDUPLICATION"
      ? "failed"
      : isProcessed
        ? "done"
        : isRejected || isFailed
          ? "pending"
          : isReceived || isProcessing
            ? "pending"
            : "done";
  const leadState = isProcessed
    ? "done"
    : isDuplicate
      ? "skipped"
      : isFailed && stage === "LEAD_CREATION"
        ? "failed"
        : "pending";
  const workflowState = isProcessed ? "done" : "pending";

  return [
    { label: "Received", state: "done" as const },
    { label: "Mapped", state: mappingState as "done" | "failed" | "pending" },
    { label: "Validated", state: validationState as "done" | "failed" | "pending" },
    { label: "Deduplicated", state: dedupState as "done" | "failed" | "pending" | "duplicate" | "skipped" },
    { label: "Lead", state: leadState as "done" | "failed" | "pending" | "skipped" },
    { label: "Workflow", state: workflowState as "done" | "pending" },
  ];
}

export default function AcquisitionEventDetailPage() {
  const params = useParams<{ configId: string; eventId: string }>();
  const configId = params?.configId ?? "";
  const eventId = params?.eventId ?? "";

  const queryClient = useQueryClient();
  const { canViewAcquisition, canEditAcquisition } = usePermissions();
  const [reprocessOpen, setReprocessOpen] = useState(false);

  const eventQuery = useLeadIngestionEvent(configId, eventId);
  const reprocess = useReprocessLeadIngestionEvent(configId);

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

  const handleReprocess = async () => {
    try {
      const result = await reprocess.mutateAsync(eventId);
      setReprocessOpen(false);
      if (result.status === "PROCESSED") {
        toast.success(`Reprocess successful — lead ${result.leadId ?? "created"}`);
      } else if (result.status === "DUPLICATE") {
        toast.success(
          `Reprocess completed — duplicate${result.leadId ? ` (lead ${result.leadId})` : ""}`
        );
      } else if (result.status === "REJECTED") {
        toast.error(`Reprocess rejected — ${result.errorCode ?? "validation failed"}`);
      } else if (result.status === "FAILED") {
        toast.error(`Reprocess failed — ${result.errorCode ?? "processing failed"}`);
      } else {
        toast.success(`Reprocess completed — ${result.status}`);
      }
    } catch (e: unknown) {
      const msg =
        e instanceof Error ? e.message : typeof e === "string" ? e : "Reprocess failed";
      // Try to extract API error message
      const apiMsg =
        (e as { response?: { data?: { error?: { message?: string } } } })?.response?.data
          ?.error?.message ?? msg;
      toast.error(apiMsg);
    }
  };

  const canReprocess =
    canEditAcquisition &&
    event &&
    (event.status === "FAILED" || event.status === "REJECTED");
  const showDuplicateNoRetry =
    event?.status === "DUPLICATE" || event?.status === "PROCESSED";

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
                  : event.status === "DUPLICATE"
                    ? "secondary"
                    : event.status === "REJECTED" || event.status === "FAILED"
                      ? "destructive"
                      : "secondary"
              }
            >
              {event.status}
            </Badge>
          )}
          {event?.failureStage && (
            <Badge variant="outline">{stageLabels[event.failureStage] ?? event.failureStage}</Badge>
          )}
          {event?.attemptCount != null && event.attemptCount > 1 && (
            <Badge variant="outline">Attempt {event.attemptCount}</Badge>
          )}
          <Button variant="outline" size="sm" onClick={handleRefresh}>
            <RefreshCw className="mr-2 h-4 w-4" /> Refresh
          </Button>
          {canReprocess && (
            <Button
              size="sm"
              onClick={() => setReprocessOpen(true)}
              disabled={reprocess.isPending}
            >
              <RefreshCw className="mr-2 h-4 w-4" />
              {reprocess.isPending ? "Reprocessing…" : "Reprocess"}
            </Button>
          )}
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
          {/* Ingestion Result + Timeline */}
          <Card>
            <CardHeader>
              <CardTitle>Ingestion Result</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* Timeline */}
              <div className="flex flex-wrap gap-2">
                {getTimeline(event).map((step) => {
                  const Icon =
                    step.state === "done"
                      ? CheckCircle2
                      : step.state === "failed"
                        ? XCircle
                        : step.state === "duplicate"
                          ? Copy
                          : step.state === "skipped"
                            ? Circle
                            : Circle;
                  const color =
                    step.state === "done"
                      ? "text-green-600"
                      : step.state === "failed"
                        ? "text-red-600"
                        : step.state === "duplicate"
                          ? "text-amber-600"
                          : "text-muted-foreground";
                  return (
                    <div
                      key={step.label}
                      className="flex items-center gap-1 text-xs"
                    >
                      <Icon className={`h-4 w-4 ${color}`} />
                      <span className={color}>{step.label}</span>
                    </div>
                  );
                })}
              </div>

              {/* Status-specific guidance */}
              {event.status === "PROCESSED" && (
                <div className="rounded-md border border-green-200 bg-green-50 p-3 text-sm dark:border-green-900 dark:bg-green-950/30">
                  <p className="font-medium text-green-700 dark:text-green-300">
                    Successfully processed
                  </p>
                  <p className="text-green-600 dark:text-green-400">
                    Lead {event.leadId ? `#${event.leadId}` : "created"} and
                    workflow event published.
                  </p>
                  {event.leadId && (
                    <Link
                      href={`/leads/${event.leadId}`}
                      className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-green-700 underline dark:text-green-300"
                    >
                      View lead <ExternalLink className="h-3 w-3" />
                    </Link>
                  )}
                </div>
              )}

              {event.status === "DUPLICATE" && (
                <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm dark:border-amber-900 dark:bg-amber-950/30">
                  <p className="font-medium text-amber-700 dark:text-amber-300">
                    Duplicate — no new lead created
                  </p>
                  <p className="text-amber-600 dark:text-amber-400">
                    A lead with this email or phone already exists.
                  </p>
                  {event.leadId ? (
                    <Link
                      href={`/leads/${event.leadId}`}
                      className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-amber-700 underline dark:text-amber-300"
                    >
                      View existing lead <ExternalLink className="h-3 w-3" />
                    </Link>
                  ) : (
                    <p className="mt-1 text-xs text-amber-600 dark:text-amber-400">
                      Existing lead could not be linked automatically.
                    </p>
                  )}
                </div>
              )}

              {(event.status === "REJECTED" || event.status === "FAILED") && (
                <div className="space-y-3">
                  <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm dark:border-red-900 dark:bg-red-950/30">
                    <p className="font-medium text-red-700 dark:text-red-300">
                      {event.failureStage
                        ? stageGuidance[event.failureStage]?.title ??
                          `${stageLabels[event.failureStage] ?? event.failureStage} failed`
                        : event.status === "REJECTED"
                          ? "Rejected"
                          : "Failed"}
                    </p>
                    {event.failureStage && (
                      <p className="text-xs text-red-600 dark:text-red-400">
                        Stage: {stageLabels[event.failureStage] ?? event.failureStage}
                        {event.attemptCount ? ` · Attempt ${event.attemptCount}` : ""}
                      </p>
                    )}
                    <p className="mt-1 text-red-600 dark:text-red-400">
                      {event.errorMessage ?? event.errorCode ?? "Processing failed"}
                    </p>
                    {event.failureStage && stageGuidance[event.failureStage] && (
                      <p className="mt-2 text-xs text-red-600 dark:text-red-400">
                        <span className="font-medium">How to fix: </span>
                        {stageGuidance[event.failureStage].fix}
                      </p>
                    )}
                    {event.failureStage === "MAPPING" ||
                    event.failureStage === "VALIDATION" ? (
                      <Link
                        href={`/acquisition/configs/${configId}/mappings`}
                        className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-red-700 underline dark:text-red-300"
                      >
                        Open mapping <ExternalLink className="h-3 w-3" />
                      </Link>
                    ) : null}
                  </div>
                  {(event.errorCode || event.errorMessage) && (
                    <div className="rounded-md border bg-muted/20 p-3 text-xs">
                      <p className="font-medium">Details</p>
                      {event.errorCode && (
                        <p className="text-muted-foreground">Code: {event.errorCode}</p>
                      )}
                      {event.errorMessage && (
                        <p className="break-words text-muted-foreground">
                          {event.errorMessage}
                        </p>
                      )}
                    </div>
                  )}
                </div>
              )}

              {showDuplicateNoRetry && event.status === "DUPLICATE" && (
                <p className="text-xs text-muted-foreground">
                  Duplicate events are not reprocessable — they are valid terminal
                  outcomes.
                </p>
              )}
              {event.status === "PROCESSED" && (
                <p className="text-xs text-muted-foreground">
                  Processed events cannot be reprocessed to avoid duplicate leads.
                </p>
              )}
              {canReprocess && (
                <p className="text-xs text-muted-foreground">
                  Fix the mapping/configuration, then reprocess to retry with the
                  stored payload and current mapping.
                </p>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Details</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-x-8 gap-y-3 text-sm md:grid-cols-2">
              <DetailRow label="Received">
                {event.receivedAt ? new Date(event.receivedAt).toLocaleString() : "—"}
              </DetailRow>
              <DetailRow label="Processed">
                {event.processedAt ? new Date(event.processedAt).toLocaleString() : "—"}
              </DetailRow>
              <DetailRow label="Stage">
                {event.failureStage ? (
                  <span className="inline-flex items-center gap-1">
                    <AlertTriangle className="h-3 w-3" />
                    {stageLabels[event.failureStage] ?? event.failureStage}
                  </span>
                ) : (
                  "—"
                )}
              </DetailRow>
              <DetailRow label="Lead">
                {event.leadId ? (
                  <Link
                    href={`/leads/${event.leadId}`}
                    className="break-all underline inline-flex items-center gap-1"
                  >
                    View lead <ExternalLink className="h-3 w-3" />
                  </Link>
                ) : (
                  <span className="break-all">—</span>
                )}
              </DetailRow>
            </CardContent>
          </Card>

          <details className="rounded-lg border bg-muted/10 p-4">
            <summary className="cursor-pointer text-sm font-medium">Advanced details</summary>
            <div className="mt-3 grid gap-3 md:grid-cols-2 text-sm">
              <DetailRow label="External Event ID">
                <span className="break-all font-mono text-xs">{event.externalEventId ?? "—"}</span>
              </DetailRow>
              <DetailRow label="Idempotency Key">
                <span className="break-all font-mono text-xs">{event.idempotencyKey ?? "—"}</span>
              </DetailRow>
              <DetailRow label="Created / Updated">
                {`${new Date(event.createdAt).toLocaleString()} · ${new Date(event.updatedAt).toLocaleString()}`}
              </DetailRow>
              <DetailRow label="Attempt">
                {event.attemptCount ?? 1}
              </DetailRow>
            </div>
            <div className="mt-4 space-y-3">
              <div>
                <p className="text-sm font-medium">Raw Payload (Technical)</p>
                <p className="text-xs text-muted-foreground">For technical troubleshooting.</p>
                <JsonBlock value={event.rawPayload} />
              </div>
              <div>
                <p className="text-sm font-medium">Headers (Technical)</p>
                <JsonBlock value={event.headers} />
              </div>
            </div>
          </details>
        </>
      )}

      <Dialog open={reprocessOpen} onOpenChange={setReprocessOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reprocess event?</DialogTitle>
            <DialogDescription>
              This will process the stored payload again using the current ingestion
              configuration and mappings. Attempt {event?.attemptCount ?? 1} →{" "}
              {(event?.attemptCount ?? 1) + 1}. The original payload will be reused;
              no duplicate lead will be created if one already exists.
            </DialogDescription>
          </DialogHeader>
          <div className="rounded-md border bg-muted/20 p-3 text-xs text-muted-foreground">
            <p>
              Config: <span className="font-medium">{configId}</span>
            </p>
            <p>
              Event: <span className="font-medium break-all">{eventId}</span>
            </p>
            {event?.failureStage && (
              <p>
                Current failure stage:{" "}
                <span className="font-medium">
                  {stageLabels[event.failureStage] ?? event.failureStage}
                </span>
              </p>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setReprocessOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleReprocess} disabled={reprocess.isPending}>
              {reprocess.isPending ? "Reprocessing…" : "Reprocess"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
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
