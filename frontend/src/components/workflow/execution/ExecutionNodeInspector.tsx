"use client";

import { useState } from "react";
import { Copy, Check, ChevronDown, AlertTriangle, CheckCircle2, XCircle, Clock, MinusCircle, Hourglass, Loader2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import type { WorkflowExecutionNodeExecutionResponse } from "@/types/workflow";
import { toast } from "sonner";
import { describeAction } from "../utils/node-config";

function formatJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
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

function truncateId(id: string | null | undefined, short = 8) {
  if (!id) return "—";
  if (id.length <= 12) return id;
  return `${id.slice(0, short)}...${id.slice(-4)}`;
}

function JsonBlock({ value, label, emptyLabel = "—" }: { value?: Record<string, unknown> | null; label: string; emptyLabel?: string }) {
  const has = value && Object.keys(value).length > 0;
  const text = has ? formatJson(value) : "";
  const [copied, setCopied] = useState(false);
  const onCopy = () => {
    if (!has) return;
    copyToClipboard(text);
    setCopied(true);
    toast.success(`${label} copied`);
    setTimeout(() => setCopied(false), 1200);
  };
  if (!has) {
    return (
      <div className="rounded-md border bg-muted/20 p-3">
        <p className="text-xs text-muted-foreground">{emptyLabel}</p>
      </div>
    );
  }
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
        <Button variant="outline" size="xs" onClick={onCopy} aria-label={`Copy ${label}`}>
          {copied ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />} {copied ? "Copied" : "Copy"}
        </Button>
      </div>
      <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-words rounded-md border bg-muted/40 p-3 font-mono text-xs leading-relaxed">
        {text}
      </pre>
    </div>
  );
}

function CopyableId({ label, value }: { label: string; value?: string | null }) {
  const [copied, setCopied] = useState(false);
  if (!value) return null;
  const onCopy = () => {
    copyToClipboard(value);
    setCopied(true);
    toast.success(`${label} copied`);
    setTimeout(() => setCopied(false), 900);
  };
  return (
    <div className="flex items-center justify-between gap-2 py-1">
      <div className="min-w-0">
        <p className="text-[11px] uppercase tracking-wide text-muted-foreground">{label}</p>
        <p className="font-mono text-xs break-all">{truncateId(value, 8)} <span className="text-muted-foreground">({value.slice(0,4)}…)</span></p>
      </div>
      <Button variant="ghost" size="xs" onClick={onCopy} aria-label={`Copy ${label}`}>
        {copied ? <Check className="h-3 w-3 text-emerald-600" /> : <Copy className="h-3 w-3" />}
      </Button>
    </div>
  );
}

function statusBadge(status: string) {
  const variant = status === "COMPLETED" ? "default" : status === "FAILED" ? "destructive" : status === "SKIPPED" ? "secondary" : "outline";
  const icon = status === "COMPLETED" ? "✓" : status === "FAILED" ? "✕" : status === "RUNNING" ? "⟳" : status === "SKIPPED" ? "○" : status === "WAITING" ? "◷" : "•";
  return { variant: variant as "default" | "destructive" | "secondary" | "outline", icon };
}

function httpStatusText(code: number): string {
  const texts: Record<number, string> = {
    200: "OK", 201: "Created", 204: "No Content", 400: "Bad Request", 401: "Unauthorized", 403: "Forbidden", 404: "Not Found", 409: "Conflict", 422: "Unprocessable", 429: "Too Many Requests", 500: "Internal Server Error", 502: "Bad Gateway", 503: "Service Unavailable",
  };
  return texts[code] ?? "";
}

function getApplicationOutcome(output: Record<string, unknown> | null | undefined): string | null {
  if (!output) return null;
  const v = output.applicationOutcome ?? output.application_outcome;
  if (typeof v === "string" && v.trim()) return v.trim().toUpperCase();
  return null;
}

function getUserMessage(output: Record<string, unknown> | null | undefined, fallbackError?: string | null): string | null {
  if (!output) return fallbackError ?? null;
  const candidates = ["userMessage", "applicationMessage", "user_message", "errorMessage", "outcomeReason"];
  for (const k of candidates) {
    const v = output[k];
    if (typeof v === "string" && v.trim()) return v.trim();
  }
  return fallbackError ?? null;
}

function extractPhone(requestBody: unknown, inputContext: Record<string, unknown> | null | undefined): string | null {
  const search = (obj: unknown): string | null => {
    if (!obj || typeof obj !== "object") return null;
    const dict = obj as Record<string, unknown>;
    for (const key of ["phone", "phoneNumber", "phone_number", "mobile", "mobileNumber", "contactPhone", "contact_phone"]) {
      for (const k of Object.keys(dict)) {
        if (k.toLowerCase() === key.toLowerCase()) {
          const v = dict[k];
          if (typeof v === "string" && v.trim()) return v.trim();
          if (typeof v === "number") return String(v);
        }
      }
    }
    // recurse one level into nested objects like body.data, body.lead, inputContext.entity etc.
    for (const v of Object.values(dict)) {
      if (v && typeof v === "object" && !Array.isArray(v)) {
        const found = search(v);
        if (found) return found;
      }
    }
    return null;
  };
  const fromBody = search(requestBody);
  if (fromBody) return fromBody;
  return search(inputContext);
}

function extractLeadLabel(inputContext: Record<string, unknown> | null | undefined): string | null {
  if (!inputContext) return null;
  const dict = inputContext as Record<string, unknown>;
  // Common entity wrappers: entity, trigger, lead, contact, etc.
  const candidates: unknown[] = [dict.entity, dict.lead, dict.contact, dict.trigger, dict];
  for (const obj of candidates) {
    if (!obj || typeof obj !== "object") continue;
    const o = obj as Record<string, unknown>;
    for (const key of ["name", "fullName", "full_name", "displayName", "title", "leadName", "contactName", "email", "id", "entityId"]) {
      for (const k of Object.keys(o)) {
        if (k.toLowerCase() === key.toLowerCase()) {
          const v = o[k];
          if (typeof v === "string" && v.trim()) return v.trim();
        }
      }
    }
    // fallback nested entity
    if (typeof o.entity === "object" && o.entity) {
      const n = extractLeadLabel(o.entity as Record<string, unknown>);
      if (n) return n;
    }
  }
  return null;
}

function friendlyErrorMessage(code: string | null, message: string | null): string | null {
  if (!code) return message;
  const map: Record<string, string> = {
    WORKFLOW_ACTION_VALUE_RESOLUTION_FAILED: "A required value was unavailable for this action.",
    WORKFLOW_HTTP_API_URL_REQUIRED: "The HTTP API URL was missing.",
    WORKFLOW_HTTP_API_INVALID_METHOD: "The HTTP method was invalid.",
    WORKFLOW_HTTP_API_CREDENTIAL_NOT_CONFIGURED: "Credentials are not configured for this API for the selected user.",
    WORKFLOW_HTTP_APPLICATION_FAILURE: "The request reached the remote service, but the service reported an error.",
    WORKFLOW_HTTP_API_EXECUTION_FAILED: "The HTTP request failed.",
    OUTBOUND_HTTP_FAILED: "The request could not be sent due to a network or connectivity issue.",
  };
  const friendly = map[code];
  if (friendly) {
    if (message && !message.includes(friendly)) return `${friendly} ${message}`.trim();
    return friendly;
  }
  return message;
}

interface Props {
  nodeKey?: string | null;
  nodeType?: string | null;
  nodeName?: string | null;
  execution?: WorkflowExecutionNodeExecutionResponse | null;
  workflowVersionId?: string | null;
  onClose?: () => void;
}

export function ExecutionNodeInspector({ nodeKey, nodeType, nodeName, execution, workflowVersionId, onClose }: Props) {
  if (!execution && !nodeKey) {
    return (
      <Card>
        <CardContent className="p-6">
          <p className="text-sm text-muted-foreground">Select a node in the graph or timeline to inspect its execution.</p>
        </CardContent>
      </Card>
    );
  }

  if (!execution) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base">{nodeName || nodeKey || "Node"}</CardTitle>
          <p className="text-xs text-muted-foreground">{nodeType ?? ""}</p>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">No execution data for this node. It was not reached in this run (pending or skipped).</p>
        </CardContent>
      </Card>
    );
  }

  const st = statusBadge(execution.status);
  const output = execution.outputContext as Record<string, unknown> | null | undefined;
  const duration = (() => {
    if (!execution.startedAt || !execution.completedAt) return "—";
    const ms = new Date(execution.completedAt).getTime() - new Date(execution.startedAt).getTime();
    if (ms < 0) return "—";
    if (ms < 1000) return `${ms}ms`;
    const s = Math.floor(ms / 1000);
    if (s < 60) return `${s}s`;
    return `${Math.floor(s / 60)}m ${s % 60}s`;
  })();

  const httpStatus = typeof output?.statusCode === "number" ? (output.statusCode as number) : typeof output?.httpStatus === "number" ? (output.httpStatus as number) : null;
  const httpResponse = (output as unknown as { response?: unknown } | null)?.response;
  const applicationOutcome = getApplicationOutcome(output);
  const userMessage = getUserMessage(output, execution.lastErrorMessage);
  const outcomeReason = typeof output?.outcomeReason === "string" ? (output.outcomeReason as string) : null;
  const transportSuccess = typeof output?.transportSuccess === "boolean" ? (output.transportSuccess as boolean) : null;
  const correlationId = typeof output?.correlationId === "string" ? (output.correlationId as string) : null;
  const selectedEdgeId = typeof output?.selectedEdgeId === "string" ? (output.selectedEdgeId as string) : null;

  const request = (output as unknown as { request?: { method?: string; url?: string; query?: unknown; headers?: Record<string, string>; body?: unknown } } | null)?.request;

  const isHttpAction = execution.nodeType === "ACTION" && (httpStatus !== null || request != null || applicationOutcome != null || (output && ("response" in output || "request" in output)));

  // Friendly display name: prefer graph node name, then describeAction fallback, then nodeKey
  const friendlyName = (() => {
    if (nodeName && nodeName.trim()) return nodeName.trim();
    // Try to derive from stored node config if available inside output? not.
    // Use describeAction helper if configuration available via header? We have nodeKey as fallback.
    // For ACTION, we can at least show nodeKey humanized (replace _ with space)
    if (execution.nodeKey && execution.nodeKey !== nodeKey) return execution.nodeKey;
    return execution.nodeKey || nodeKey || "Node";
  })();

  const httpUrlShort = (() => {
    if (!request?.url) return null;
    try {
      const u = new URL(String(request.url));
      return `${u.hostname}${u.pathname}`.replace(/\/$/, "");
    } catch {
      return String(request.url).slice(0, 48);
    }
  })();

  const friendlyStatusLabel = (() => {
    if (execution.status === "FAILED") {
      if (isHttpAction && applicationOutcome === "FAILURE") return "FAILED";
      return "FAILED";
    }
    if (execution.status === "COMPLETED") {
      if (isHttpAction && applicationOutcome === "FAILURE") return "FAILED";
      if (isHttpAction && applicationOutcome === "UNKNOWN" && httpStatus !== null && httpStatus >= 200 && httpStatus < 300) return "HTTP REQUEST COMPLETED";
      if (isHttpAction && applicationOutcome === "SUCCESS") return "COMPLETED";
      return "COMPLETED";
    }
    return execution.status;
  })();

  const primaryVariant = execution.status === "FAILED" || (isHttpAction && applicationOutcome === "FAILURE") ? "destructive" : applicationOutcome === "UNKNOWN" ? "outline" : st.variant;
  const primaryIcon = execution.status === "FAILED" || applicationOutcome === "FAILURE" ? "🔴" : applicationOutcome === "UNKNOWN" ? "◐" : st.icon;

  const phone = extractPhone(request?.body, execution.inputContext);
  const leadLabel = extractLeadLabel(execution.inputContext);
  const entityIdFromInput = (() => {
    const ic = execution.inputContext as Record<string, unknown> | null | undefined;
    if (!ic) return null;
    const e = ic?.entity as Record<string, unknown> | undefined;
    if (e?.id) return String(e.id);
    if (ic?.entityId) return String(ic.entityId);
    return null;
  })();

  const friendlyLastError = friendlyErrorMessage(execution.lastErrorCode, execution.lastErrorMessage || userMessage);

  return (
    <Card className="overflow-hidden">
      <CardHeader className="space-y-2">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <CardTitle className="text-base truncate" title={friendlyName}>{friendlyName}</CardTitle>
            <p className="text-xs uppercase tracking-wide text-muted-foreground">{execution.nodeType} · {execution.nodeKey}</p>
          </div>
          <Badge variant={primaryVariant} className="shrink-0">{primaryIcon} {friendlyStatusLabel}</Badge>
        </div>
        {execution.lastErrorCode && (
          <div className="rounded-md border border-red-200 bg-red-50 p-2 dark:border-red-900 dark:bg-red-950/30">
            <p className="text-xs font-medium text-red-700 dark:text-red-300 break-all">{execution.lastErrorCode}</p>
            {friendlyLastError && <p className="mt-1 text-xs text-red-600 dark:text-red-400 break-words">{friendlyLastError}</p>}
          </div>
        )}
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
        {/* Human-friendly primary section for HTTP actions */}
        {isHttpAction ? (
          <div className="space-y-3">
            {/* Main outcome card */}
            <div className={`rounded-lg border p-3 ${applicationOutcome === "FAILURE" || execution.status === "FAILED" ? "border-red-200 bg-red-50/50 dark:border-red-900 dark:bg-red-950/20" : applicationOutcome === "UNKNOWN" ? "border-amber-200 bg-amber-50/40 dark:border-amber-900 dark:bg-amber-950/20" : "border-emerald-200 bg-emerald-50/30 dark:border-emerald-900 dark:bg-emerald-950/20"}`}>
              <div className="flex items-start gap-2">
                <span className="text-lg leading-none" aria-hidden>{applicationOutcome === "FAILURE" || execution.status === "FAILED" ? "🔴" : applicationOutcome === "UNKNOWN" ? "◐" : "✓"}</span>
                <div className="flex-1 min-w-0">
                  <p className={`text-sm font-semibold ${applicationOutcome === "FAILURE" || execution.status === "FAILED" ? "text-red-700 dark:text-red-300" : applicationOutcome === "UNKNOWN" ? "text-amber-800 dark:text-amber-200" : "text-emerald-800 dark:text-emerald-200"}`}>
                    {applicationOutcome === "FAILURE" || execution.status === "FAILED" ? "Failed" : applicationOutcome === "UNKNOWN" ? "HTTP request completed" : "Completed"}
                    <span className="ml-2 text-xs font-normal text-muted-foreground">{execution.nodeKey}</span>
                  </p>
                  {userMessage ? (
                    <>
                      <p className="mt-1 text-xs uppercase tracking-wide text-muted-foreground">Reason</p>
                      <p className="font-medium break-words">{userMessage}</p>
                    </>
                  ) : outcomeReason ? (
                    <>
                      <p className="mt-1 text-xs uppercase tracking-wide text-muted-foreground">Reason</p>
                      <p className="text-sm break-words">{outcomeReason}</p>
                    </>
                  ) : execution.status === "FAILED" ? null : (
                    <p className="mt-1 text-xs text-muted-foreground">{applicationOutcome === "UNKNOWN" ? "Remote response received — Unable to determine application result. Review response below." : applicationOutcome === "SUCCESS" ? "Remote service reported success." : ""}</p>
                  )}
                  {(applicationOutcome === "FAILURE" || execution.status === "FAILED") && !userMessage && outcomeReason && (
                    <p className="mt-2 text-xs text-muted-foreground">The request reached the remote service, but the service reported an error.</p>
                  )}
                  {applicationOutcome === "UNKNOWN" && httpStatus !== null && httpStatus >= 200 && httpStatus < 300 && (
                    <p className="mt-1 text-xs text-amber-700 dark:text-amber-300">Application result: Unable to determine</p>
                  )}
                </div>
              </div>

              <div className="mt-3 grid grid-cols-2 gap-3 text-xs">
                {leadLabel && (
                  <div>
                    <p className="uppercase tracking-wide text-muted-foreground text-[11px]">Lead / Entity</p>
                    <p className="font-medium break-all">{leadLabel}</p>
                  </div>
                )}
                {entityIdFromInput && !leadLabel && (
                  <div>
                    <p className="uppercase tracking-wide text-muted-foreground text-[11px]">Entity</p>
                    <p className="font-mono text-xs break-all">{truncateId(entityIdFromInput)}</p>
                  </div>
                )}
                {phone && (
                  <div>
                    <p className="uppercase tracking-wide text-muted-foreground text-[11px]">Phone</p>
                    <p className="font-medium font-mono">{phone}</p>
                  </div>
                )}
                {httpStatus !== null && (
                  <div>
                    <p className="uppercase tracking-wide text-muted-foreground text-[11px]">HTTP</p>
                    <p className={`inline-flex rounded border px-1.5 py-0.5 text-xs font-semibold ${httpStatus >= 200 && httpStatus < 300 ? "bg-emerald-50 border-emerald-200 text-emerald-700 dark:bg-emerald-950/30" : "bg-red-50 border-red-200 text-red-700 dark:bg-red-950/30"}`}>{httpStatus} {httpStatusText(httpStatus)}</p>
                    {applicationOutcome && <p className="mt-1 text-[11px] text-muted-foreground">Application: {applicationOutcome}</p>}
                  </div>
                )}
                <div>
                  <p className="uppercase tracking-wide text-muted-foreground text-[11px]">Attempt</p>
                  <p className="font-medium">{execution.attemptCount ?? 1}</p>
                </div>
                <div>
                  <p className="uppercase tracking-wide text-muted-foreground text-[11px]">Duration</p>
                  <p className="font-medium">{duration}</p>
                </div>
                {transportSuccess !== null && (
                  <div>
                    <p className="uppercase tracking-wide text-muted-foreground text-[11px]">Transport</p>
                    <p className={transportSuccess ? "text-emerald-700" : "text-red-600"}>{transportSuccess ? "Request sent" : "Not sent"}</p>
                  </div>
                )}
              </div>

              {request?.method || httpUrlShort ? (
                <div className="mt-3 rounded border bg-white p-2 dark:bg-slate-900">
                  <p className="text-[11px] uppercase tracking-wide text-muted-foreground">Request</p>
                  <p className="font-mono text-xs break-all">{request?.method ?? ""} {httpUrlShort ?? request?.url ?? ""}</p>
                </div>
              ) : null}
            </div>

            {/* Response summary secondary */}
            {httpStatus !== null && (
              <div className="rounded-md border bg-white p-3 dark:bg-slate-900">
                <p className="text-xs font-medium">Response</p>
                <div className="mt-1 flex flex-wrap items-center gap-2">
                  <span className={`rounded border px-1.5 py-0.5 text-xs font-semibold ${httpStatus >= 200 && httpStatus < 300 ? "bg-emerald-50 border-emerald-200 text-emerald-700" : "bg-red-50 border-red-200 text-red-700"}`}>HTTP {httpStatus} {httpStatusText(httpStatus)}</span>
                  {applicationOutcome && <span className={`rounded border px-1.5 py-0.5 text-xs ${applicationOutcome === "FAILURE" ? "bg-red-50 border-red-200 text-red-700" : applicationOutcome === "SUCCESS" ? "bg-emerald-50 border-emerald-200 text-emerald-700" : "bg-amber-50 border-amber-200 text-amber-700"}`}>Application {applicationOutcome}</span>}
                  {correlationId && <span className="font-mono text-[11px] text-muted-foreground">correlation {truncateId(correlationId, 6)}</span>}
                </div>
                {outcomeReason && <p className="mt-2 text-xs text-muted-foreground">Outcome reason: <span className="font-medium text-foreground">{outcomeReason}</span></p>}
                {httpResponse !== undefined && (
                  <details className="mt-2">
                    <summary className="cursor-pointer text-xs font-medium text-muted-foreground hover:text-foreground">Response body</summary>
                    <pre className="mt-1 max-h-48 overflow-auto whitespace-pre-wrap break-words rounded-md border bg-muted/40 p-2 font-mono text-xs">{typeof httpResponse === "string" ? httpResponse : JSON.stringify(httpResponse, null, 2)}</pre>
                  </details>
                )}
              </div>
            )}

            {/* Request snapshot secondary */}
            {request && (
              <div className="rounded-md border bg-white p-3 dark:bg-slate-900">
                <p className="text-xs font-medium">Request details</p>
                <div className="mt-2 space-y-2 text-xs">
                  {request.method && <p><span className="text-muted-foreground">Method:</span> <span className="font-mono rounded bg-muted px-1">{String(request.method)}</span></p>}
                  {request.url && <p className="break-all"><span className="text-muted-foreground">URL:</span> <span className="font-mono rounded bg-muted px-1 break-all">{String(request.url)}</span></p>}
                  {request.query != null && typeof request.query === "object" && Object.keys(request.query as Record<string, unknown>).length > 0 && (
                    <div>
                      <p className="font-medium text-muted-foreground">Query</p>
                      <pre className="mt-1 max-h-32 overflow-auto rounded-md border bg-muted/40 p-2 font-mono text-xs">{JSON.stringify(request.query, null, 2)}</pre>
                    </div>
                  )}
                  {request.headers != null && typeof request.headers === "object" && Object.keys(request.headers as Record<string, unknown>).length > 0 && (
                    <div>
                      <p className="font-medium text-muted-foreground">Headers (redacted)</p>
                      <pre className="mt-1 max-h-32 overflow-auto rounded-md border bg-muted/40 p-2 font-mono text-xs">{JSON.stringify(request.headers, null, 2)}</pre>
                    </div>
                  )}
                  {request.body != null && (
                    <div>
                      <p className="font-medium text-muted-foreground">Body (redacted)</p>
                      <pre className="mt-1 max-h-40 overflow-auto whitespace-pre-wrap break-words rounded-md border bg-muted/40 p-2 font-mono text-xs">{typeof request.body === "string" ? request.body : JSON.stringify(request.body, null, 2)}</pre>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        ) : (
          /* Non-HTTP primary */
          <div className="grid grid-cols-2 gap-3">
            <div>
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Attempts</p>
              <p className="font-medium">{execution.attemptCount ?? 1}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Duration</p>
              <p className="font-medium">{duration}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Started</p>
              <p className="font-mono text-xs">{execution.startedAt ? new Date(execution.startedAt).toLocaleString() : "—"}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Completed</p>
              <p className="font-mono text-xs">{execution.completedAt ? new Date(execution.completedAt).toLocaleString() : "—"}</p>
            </div>
            {execution.nextAttemptAt && (
              <div className="col-span-2">
                <p className="text-xs uppercase tracking-wide text-muted-foreground">{execution.nodeType === "WAIT" ? "Waiting until" : "Next attempt"}</p>
                <p className="font-mono text-xs text-amber-700">{new Date(execution.nextAttemptAt).toLocaleString()}</p>
              </div>
            )}
            {httpStatus !== null && (
              <div className="col-span-2">
                <p className="text-xs uppercase tracking-wide text-muted-foreground">HTTP status</p>
                <p className={`inline-flex rounded border px-1.5 py-0.5 text-xs font-semibold ${httpStatus >= 200 && httpStatus < 300 ? "bg-emerald-50 border-emerald-200 text-emerald-700" : "bg-red-50 border-red-200 text-red-700"}`}>Status {httpStatus}</p>
              </div>
            )}
          </div>
        )}

        {/* Condition / Branch rule-level debugging — optional, additive */}
        {(() => {
          const out = execution.outputContext as unknown as {
            result?: boolean;
            outcome?: string;
            logic?: string;
            ruleResults?: Array<{ index: number; field: string; operator: string; expected: unknown; actual: unknown; passed: boolean }>;
          } | null;
          const rules = out?.ruleResults;
          if (!rules || !Array.isArray(rules) || rules.length === 0) return null;
          const isCondition = execution.nodeType === "CONDITION";
          const finalOutcome = isCondition ? (out?.result ? "TRUE" : "FALSE") : out?.outcome ?? (out?.result ? "TRUE" : "FALSE");
          const logic = out?.logic ?? (isCondition ? "AND" : "AND");
          return (
            <>
              <Separator />
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Condition evaluation</p>
                <p className="mt-1 text-xs text-muted-foreground">Logic: {logic} · Final outcome: <span className="font-medium text-foreground">{String(finalOutcome)}</span></p>
                <ul className="mt-2 space-y-2" role="list" aria-label="Condition rule results">
                  {rules.map((r, i) => (
                    <li
                      key={r.index ?? i}
                      className={`rounded-md border p-2 ${r.passed ? "border-emerald-200 bg-emerald-50/50 dark:border-emerald-900 dark:bg-emerald-950/20" : "border-red-200 bg-red-50/50 dark:border-red-900 dark:bg-red-950/20"}`}
                      role="listitem"
                      aria-label={`Condition rule ${i + 1} ${r.passed ? "passed" : "failed"}`}
                    >
                      <div className="flex items-center gap-1.5">
                        <span aria-hidden="true" className={`text-sm ${r.passed ? "text-emerald-600" : "text-red-600"}`}>{r.passed ? "✓" : "✕"}</span>
                        <span className="text-xs font-medium">{r.passed ? "Passed" : "Failed"}</span>
                        <span className="text-xs text-muted-foreground">Rule {i + 1}</span>
                      </div>
                      <p className="mt-1 font-mono text-xs break-all">
                        <span className="font-medium">{String(r.field)}</span> <span className="text-muted-foreground">{String(r.operator)}</span> <span className="rounded bg-muted px-1">{r.expected == null ? "null" : typeof r.expected === "string" ? `"${String(r.expected)}"` : JSON.stringify(r.expected)}</span>
                      </p>
                      <p className="mt-1 text-xs">
                        <span className="text-muted-foreground">Actual:</span> <span className="font-mono rounded bg-muted px-1 break-all">{r.actual == null ? "null" : typeof r.actual === "string" ? `"${String(r.actual)}"` : JSON.stringify(r.actual)}</span>
                      </p>
                      <p className="text-xs">
                        <span className="text-muted-foreground">Expected:</span> <span className="font-mono rounded bg-muted px-1 break-all">{r.expected == null ? "null" : typeof r.expected === "string" ? `"${String(r.expected)}"` : JSON.stringify(r.expected)}</span>
                      </p>
                    </li>
                  ))}
                </ul>
              </div>
            </>
          );
        })()}

        {/* Expandable Technical details */}
        <Separator />
        <details className="rounded-md border bg-muted/10">
          <summary className="cursor-pointer list-none px-3 py-2 flex items-center justify-between text-xs font-medium uppercase tracking-wide text-muted-foreground hover:text-foreground">
            <span>Technical details</span>
            <ChevronDown className="h-4 w-4" />
          </summary>
          <div className="px-3 pb-3 space-y-3">
            <div className="grid gap-1">
              <CopyableId label="Node ID" value={execution.nodeId} />
              <CopyableId label="Node Execution ID" value={execution.id} />
              <CopyableId label="Workflow Version ID" value={workflowVersionId ?? undefined} />
              {correlationId && <CopyableId label="Correlation ID" value={correlationId} />}
              {selectedEdgeId && <CopyableId label="Selected Edge ID" value={selectedEdgeId} />}
              <div className="py-1">
                <p className="text-[11px] uppercase tracking-wide text-muted-foreground">Node Key / Type</p>
                <p className="font-mono text-xs">{execution.nodeKey} · {execution.nodeType}</p>
              </div>
              {execution.lastErrorCode && (
                <div className="py-1">
                  <p className="text-[11px] uppercase tracking-wide text-muted-foreground">Technical error</p>
                  <p className="font-mono text-xs break-all">{execution.lastErrorCode} {execution.lastErrorMessage ? `— ${execution.lastErrorMessage}` : ""}</p>
                </div>
              )}
            </div>

            <Separator />

            <div className="space-y-2">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Raw contexts</p>
              <JsonBlock value={execution.inputContext} label="Input Context" emptyLabel="No input context" />
              <JsonBlock value={execution.outputContext} label="Output Context" emptyLabel="No output context" />
            </div>

            {execution.status === "SKIPPED" && (
              <p className="rounded-md bg-slate-50 p-2 text-xs text-slate-600 dark:bg-slate-900">This node was skipped — its branch was not taken.</p>
            )}
          </div>
        </details>
      </CardContent>
    </Card>
  );
}
