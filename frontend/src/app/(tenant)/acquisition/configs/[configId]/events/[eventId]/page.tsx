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
  Check,
} from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
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
    title: "Duplicate lead detected",
    desc: "This lead was not created because a matching lead already exists.",
    fix: "Review the existing lead. No retry is needed — the same payload will produce the same result.",
  },
  LEAD_CREATION: {
    title: "Lead creation failed",
    desc: "The lead could not be created despite valid data.",
    fix: "Check lead status/source configuration and required custom fields, then reprocess.",
  },
  UNKNOWN: {
    title: "We couldn't process this lead",
    desc: "An unexpected error occurred. Please try again or contact an administrator.",
    fix: "Please try again. If the issue persists, contact support with the event ID.",
  },
};

function truncateId(id: string | null | undefined) {
  if (!id) return "—";
  if (id.length <= 12) return id;
  return `${id.slice(0, 8)}...${id.slice(-4)}`;
}

function copyToClipboard(text: string) {
  if (navigator.clipboard?.writeText) navigator.clipboard.writeText(text);
  else {
    const ta = document.createElement("textarea");
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand("copy");
    document.body.removeChild(ta);
  }
}

function getDuplicateMatchLabel(event: LeadIngestionEventDetailResponse): string | null {
  const t = (event as unknown as { duplicateMatchType?: string }).duplicateMatchType;
  if (t) {
    const upper = String(t).toUpperCase();
    if (upper === "PHONE") return "Phone number";
    if (upper === "EMAIL") return "Email address";
    if (upper === "BOTH") return "Phone number and email";
    if (upper === "UNKNOWN") return null;
    return t;
  }
  const msg = (event.userMessage ?? event.errorMessage ?? "").toLowerCase();
  const hasPhone = msg.includes("phone");
  const hasEmail = msg.includes("email");
  if (hasPhone && hasEmail) return "Phone number and email";
  if (hasPhone) return "Phone number";
  if (hasEmail) return "Email address";
  return null;
}

function getUserMessage(event: LeadIngestionEventDetailResponse): string | null {
  // Prefer new userMessage, fallback to errorMessage, but filter out technical rollback
  const raw = (event as unknown as { userMessage?: string }).userMessage ?? event.errorMessage;
  if (!raw) return null;
  const lower = raw.toLowerCase();
  if (lower.includes("transaction silently rolled back") || lower.includes("rollback-only") || lower.includes("unexpectedrollback")) {
    return "We couldn't process this lead right now. Please try again or contact an administrator.";
  }
  return raw;
}

function getTechnicalMessage(event: LeadIngestionEventDetailResponse): string | null {
  const t = (event as unknown as { technicalMessage?: string }).technicalMessage;
  if (t && t.trim()) return t.trim();
  const raw = event.errorMessage ?? "";
  const lower = raw.toLowerCase();
  if (lower.includes("transaction silently") || lower.contains === undefined) {
    // if lower contains technical, we already masked userMessage, return raw for technical
    if (lower.includes("transaction silently") || lower.includes("rollback-only")) return raw;
  }
  return null;
}

function isRetryable(event: LeadIngestionEventDetailResponse): boolean {
  const r = (event as unknown as { retryable?: boolean }).retryable;
  if (typeof r === "boolean") return r;
  // fallback
  return event.status === "FAILED" || event.status === "REJECTED";
}

function getTimeline(event: LeadIngestionEventDetailResponse) {
  const isProcessed = event.status === "PROCESSED";
  const isDuplicate = event.status === "DUPLICATE";
  const isRejected = event.status === "REJECTED";
  const isFailed = event.status === "FAILED";
  const isProcessing = event.status === "PROCESSING";
  const isReceived = event.status === "RECEIVED";
  const stage = event.failureStage;

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
        : isRejected
          ? "pending"
          : isFailed
            ? "failed"
            : "pending";
  const workflowState = isProcessed ? "done" : isDuplicate ? "skipped" : "pending";

  return [
    { label: "Received", state: "done" as const },
    { label: "Mapped", state: mappingState as "done" | "failed" | "pending" },
    { label: "Validated", state: validationState as "done" | "failed" | "pending" },
    { label: "Deduplicated", state: dedupState as "done" | "failed" | "pending" | "duplicate" | "skipped" },
    { label: "Lead", state: leadState as "done" | "failed" | "pending" | "skipped" },
    { label: "Workflow", state: workflowState as "done" | "pending" | "skipped" },
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

  const userMessage = event ? getUserMessage(event) : null;
  const technicalMessage = event ? getTechnicalMessage(event) : null;
  const matchLabel = event ? getDuplicateMatchLabel(event) : null;
  const retryable = event ? isRetryable(event) : false;

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
              {/* Timeline - human-readable */}
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
                          : step.state === "skipped"
                            ? "text-muted-foreground"
                            : "text-muted-foreground";
                  const label =
                    step.label === "Deduplicated" && event.status === "DUPLICATE"
                      ? "Deduplication ✓"
                      : step.label === "Lead" && event.status === "DUPLICATE"
                        ? "Lead — Skipped"
                        : step.label === "Workflow" && event.status === "DUPLICATE"
                          ? "Workflow — Skipped"
                          : step.label;
                  return (
                    <div
                      key={step.label}
                      className="flex items-center gap-1 text-xs"
                    >
                      <Icon className={`h-4 w-4 ${color}`} />
                      <span className={color}>{label}</span>
                    </div>
                  );
                })}
              </div>

              {/* Status-specific guidance - human-friendly primary */}
              {event.status === "PROCESSED" && (
                <div className="rounded-md border border-green-200 bg-green-50 p-3 text-sm dark:border-green-900 dark:bg-green-950/30">
                  <p className="font-medium text-green-700 dark:text-green-300">
                    🟢 Lead created successfully
                  </p>
                  <p className="text-green-600 dark:text-green-400">
                    {userMessage ?? `Lead ${event.leadId ? `#${truncateId(event.leadId)}` : "created"} and workflow event published.`}
                  </p>
                  <div className="mt-2 grid grid-cols-2 gap-2 text-xs">
                    <div>
                      <p className="uppercase tracking-wide text-muted-foreground">Stage</p>
                      <p className="font-medium">Lead Creation</p>
                    </div>
                    <div>
                      <p className="uppercase tracking-wide text-muted-foreground">Attempt</p>
                      <p className="font-medium">{event.attemptCount ?? 1}</p>
                    </div>
                  </div>
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
                    🟡 Duplicate lead detected
                  </p>
                  <p className="text-amber-600 dark:text-amber-400">
                    {userMessage ?? "This lead was not created because a matching lead already exists."}
                  </p>
                  <div className="mt-3 grid grid-cols-2 gap-3 text-xs">
                    {matchLabel && (
                      <div>
                        <p className="uppercase tracking-wide text-muted-foreground">Match</p>
                        <p className="font-medium text-amber-700 dark:text-amber-300">{matchLabel}</p>
                      </div>
                    )}
                    <div>
                      <p className="uppercase tracking-wide text-muted-foreground">Stage</p>
                      <p className="font-medium">Deduplication</p>
                    </div>
                    <div>
                      <p className="uppercase tracking-wide text-muted-foreground">Attempt</p>
                      <p className="font-medium">{event.attemptCount ?? 1}</p>
                    </div>
                    {event.leadId && (
                      <div>
                        <p className="uppercase tracking-wide text-muted-foreground">Existing lead</p>
                        <p className="font-mono text-xs">{truncateId(event.leadId)}</p>
                      </div>
                    )}
                  </div>
                  <div className="mt-2 flex flex-wrap gap-2">
                    {event.leadId ? (
                      <Link
                        href={`/leads/${event.leadId}`}
                        className="inline-flex items-center gap-1 rounded-md border border-amber-300 bg-white px-2.5 py-1 text-xs font-medium text-amber-700 hover:bg-amber-50 dark:border-amber-800 dark:bg-amber-900/30 dark:text-amber-300"
                      >
                        View existing lead <ExternalLink className="h-3 w-3" />
                      </Link>
                    ) : (
                      <p className="text-xs text-amber-600 dark:text-amber-400">
                        Existing lead could not be linked automatically.
                      </p>
                    )}
                  </div>
                  <p className="mt-2 text-xs text-muted-foreground">
                    What you can do: Review the existing lead. No retry is needed — the same payload will produce the same duplicate result.
                  </p>
                </div>
              )}

              {(event.status === "REJECTED" || event.status === "FAILED") && (
                <div className="space-y-3">
                  <div className={`rounded-md border p-3 text-sm ${event.status === "REJECTED" ? "border-orange-200 bg-orange-50 dark:border-orange-900 dark:bg-orange-950/30" : "border-red-200 bg-red-50 dark:border-red-900 dark:bg-red-950/30"}`}>
                    <p className={`font-medium ${event.status === "REJECTED" ? "text-orange-700 dark:text-orange-300" : "text-red-700 dark:text-red-300"}`}>
                      {event.status === "REJECTED"
                        ? event.failureStage === "VALIDATION"
                          ? "🟠 Lead information needs attention"
                          : event.failureStage
                            ? stageGuidance[event.failureStage]?.title ?? `${stageLabels[event.failureStage] ?? event.failureStage} failed`
                            : "Rejected"
                        : event.failureStage === "VALIDATION"
                          ? "🟠 Lead information needs attention"
                          : "🔴 We couldn't process this lead"}
                    </p>
                    {event.failureStage && event.failureStage !== "UNKNOWN" && (
                      <p className={`text-xs ${event.status === "REJECTED" ? "text-orange-600 dark:text-orange-400" : "text-red-600 dark:text-red-400"}`}>
                        Stage: {stageLabels[event.failureStage] ?? event.failureStage}
                        {event.attemptCount ? ` · Attempt ${event.attemptCount}` : ""}
                      </p>
                    )}
                    {event.failureStage === "UNKNOWN" && (
                      <p className="text-xs text-muted-foreground">Stage: {event.failureStage} · Attempt {event.attemptCount ?? 1}</p>
                    )}
                    <p className={`mt-1 ${event.status === "REJECTED" ? "text-orange-600 dark:text-orange-400" : "text-red-600 dark:text-red-400"}`}>
                      {userMessage ?? event.errorCode ?? "Processing failed"}
                    </p>
                    {event.failureStage && stageGuidance[event.failureStage] && event.status !== "DUPLICATE" && (
                      <p className="mt-2 text-xs text-muted-foreground">
                        <span className="font-medium">What you can do: </span>
                        {event.failureStage === "UNKNOWN"
                          ? "We couldn't process this lead right now. Please try again or contact an administrator."
                          : stageGuidance[event.failureStage].fix}
                      </p>
                    )}
                    {retryable ? (
                      <p className="mt-1 text-xs text-muted-foreground">Retry is available via Reprocess.</p>
                    ) : (
                      event.status === "FAILED" && <p className="mt-1 text-xs text-muted-foreground">This failure may be retryable after fixing the cause.</p>
                    )}
                    {event.failureStage === "MAPPING" ||
                    event.failureStage === "VALIDATION" ? (
                      <Link
                        href={`/acquisition/configs/${configId}/mappings`}
                        className={`mt-2 inline-flex items-center gap-1 text-xs font-medium underline ${event.status === "REJECTED" ? "text-orange-700 dark:text-orange-300" : "text-red-700 dark:text-red-300"}`}
                      >
                        Open mapping <ExternalLink className="h-3 w-3" />
                      </Link>
                    ) : null}
                  </div>
                  {(event.errorCode || userMessage) && (
                    <div className="rounded-md border bg-white p-3 text-xs dark:bg-slate-900">
                      <p className="font-medium">Resolution hint</p>
                      {event.errorCode && (
                        <p className="text-muted-foreground">Code: {event.errorCode}</p>
                      )}
                      {userMessage && (
                        <p className="break-words text-muted-foreground">
                          {userMessage}
                        </p>
                      )}
                      {retryable && (
                        <Badge variant="outline" className="mt-2">Retryable</Badge>
                      )}
                      {!retryable && event.status === "REJECTED" && (
                        <Badge variant="secondary" className="mt-2">Not retryable as-is — fix input</Badge>
                      )}
                    </div>
                  )}
                </div>
              )}

              {showDuplicateNoRetry && event.status === "DUPLICATE" && (
                <p className="text-xs text-muted-foreground">
                  Duplicate events are not reprocessable — they are valid terminal outcomes.
                </p>
              )}
              {event.status === "PROCESSED" && (
                <p className="text-xs text-muted-foreground">
                  Processed events cannot be reprocessed to avoid duplicate leads.
                </p>
              )}
              {canReprocess && (
                <p className="text-xs text-muted-foreground">
                  Fix the mapping/configuration, then reprocess to retry with the stored payload and current mapping.
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
                ) : event.status === "DUPLICATE" ? (
                  <span className="inline-flex items-center gap-1">Deduplication</span>
                ) : event.status === "PROCESSED" ? (
                  <span className="inline-flex items-center gap-1 text-green-600">Lead Creation</span>
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
              {matchLabel && event.status === "DUPLICATE" && (
                <DetailRow label="Match">{matchLabel}</DetailRow>
              )}
              <DetailRow label="Attempt">{event.attemptCount ?? 1}</DetailRow>
              <DetailRow label="Duration">
                {event.receivedAt && event.processedAt
                  ? (() => {
                      const ms = new Date(event.processedAt).getTime() - new Date(event.receivedAt).getTime();
                      if (ms < 1000) return `${ms}ms`;
                      const s = Math.floor(ms / 1000);
                      return s < 60 ? `${s}s` : `${Math.floor(s / 60)}m ${s % 60}s`;
                    })()
                  : "—"}
              </DetailRow>
            </CardContent>
          </Card>

          <details className="rounded-lg border bg-muted/10 p-4">
            <summary className="cursor-pointer text-sm font-medium flex items-center justify-between">
              <span>Technical details</span>
              <span className="text-xs text-muted-foreground">For developers & support</span>
            </summary>
            <div className="mt-3 grid gap-3 md:grid-cols-2 text-sm">
              <TechnicalRow label="Event ID" value={event.id} />
              <TechnicalRow label="Ingestion Config ID" value={event.ingestionConfigId} />
              <TechnicalRow label="Lead ID" value={event.leadId} />
              <TechnicalRow label="External Event ID" value={event.externalEventId} />
              <TechnicalRow label="Idempotency Key" value={event.idempotencyKey} />
              <DetailRow label="Created / Updated">
                {`${new Date(event.createdAt).toLocaleString()} · ${new Date(event.updatedAt).toLocaleString()}`}
              </DetailRow>
              <DetailRow label="Attempt">{event.attemptCount ?? 1}</DetailRow>
              <DetailRow label="Error Code">
                <span className="font-mono text-xs break-all">{event.errorCode ?? "—"}</span>
              </DetailRow>
              {event.failureStage && (
                <DetailRow label="Failure Stage">
                  <span className="font-mono text-xs">{event.failureStage}</span>
                </DetailRow>
              )}
              {event.status === "DUPLICATE" && (event as unknown as { duplicateMatchType?: string }).duplicateMatchType && (
                <DetailRow label="Duplicate Match Type">
                  <span className="font-mono text-xs">{(event as unknown as { duplicateMatchType: string }).duplicateMatchType}</span>
                </DetailRow>
              )}
              <DetailRow label="Retryable">
                <Badge variant={retryable ? "outline" : "secondary"}>{retryable ? "Yes" : "No"}</Badge>
              </DetailRow>
              {technicalMessage && (
                <div className="md:col-span-2">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Technical Message</p>
                  <p className="font-mono text-xs break-all bg-muted/40 p-2 rounded border">{technicalMessage}</p>
                </div>
              )}
              {event.errorCode && (
                <div className="md:col-span-2">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Error Details</p>
                  <p className="font-mono text-xs break-all">Code: {event.errorCode}</p>
                  {event.errorMessage && <p className="font-mono text-xs break-words">Message: {event.errorMessage}</p>}
                  {getTechnicalMessage(event) && getTechnicalMessage(event) !== getUserMessage(event) && (
                    <p className="font-mono text-xs break-words text-muted-foreground">Technical: {getTechnicalMessage(event)}</p>
                  )}
                </div>
              )}
            </div>
            <Separator className="my-4" />
            <div className="space-y-3">
              <div>
                <p className="text-sm font-medium">Raw Payload (Technical)</p>
                <p className="text-xs text-muted-foreground">For technical troubleshooting — contains original payload.</p>
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
            {event?.status === "DUPLICATE" && (
              <p className="text-amber-600">Duplicate events are not reprocessable.</p>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setReprocessOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleReprocess} disabled={reprocess.isPending || event?.status === "DUPLICATE"}>
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

function TechnicalRow({ label, value }: { label: string; value?: string | null }) {
  const [copied, setCopied] = useState(false);
  if (!value) {
    return (
      <DetailRow label={label}>
        <span className="font-mono text-xs">—</span>
      </DetailRow>
    );
  }
  const onCopy = () => {
    copyToClipboard(value);
    setCopied(true);
    toast.success(`${label} copied`);
    setTimeout(() => setCopied(false), 1000);
  };
  return (
    <div>
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </p>
      <div className="flex items-center gap-2">
        <span className="font-mono text-xs break-all">{truncateId(value)}</span>
        <Button variant="ghost" size="xs" onClick={onCopy} aria-label={`Copy ${label}`}>
          {copied ? <Check className="h-3 w-3 text-green-600" /> : <Copy className="h-3 w-3" />}
        </Button>
      </div>
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
