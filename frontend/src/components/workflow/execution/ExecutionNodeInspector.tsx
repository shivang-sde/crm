"use client";

import { useState } from "react";
import { Copy, Check, ChevronDown } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import type { WorkflowExecutionNodeExecutionResponse } from "@/types/workflow";
import { toast } from "sonner";

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

function statusBadge(status: string) {
  const variant = status === "COMPLETED" ? "default" : status === "FAILED" ? "destructive" : status === "SKIPPED" ? "secondary" : "outline";
  const icon = status === "COMPLETED" ? "✓" : status === "FAILED" ? "✕" : status === "RUNNING" ? "⟳" : status === "SKIPPED" ? "○" : status === "WAITING" ? "◷" : "•";
  return { variant: variant as "default" | "destructive" | "secondary" | "outline", icon };
}

interface Props {
  nodeKey?: string | null;
  nodeType?: string | null;
  execution?: WorkflowExecutionNodeExecutionResponse | null;
  onClose?: () => void;
}

export function ExecutionNodeInspector({ nodeKey, nodeType, execution, onClose }: Props) {
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
          <CardTitle className="text-base">{nodeKey ?? "Node"}</CardTitle>
          <p className="text-xs text-muted-foreground">{nodeType ?? ""}</p>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">No execution data for this node. It was not reached in this run (pending or skipped).</p>
        </CardContent>
      </Card>
    );
  }

  const st = statusBadge(execution.status);
  const duration = (() => {
    if (!execution.startedAt || !execution.completedAt) return "—";
    const ms = new Date(execution.completedAt).getTime() - new Date(execution.startedAt).getTime();
    if (ms < 0) return "—";
    if (ms < 1000) return `${ms}ms`;
    const s = Math.floor(ms / 1000);
    if (s < 60) return `${s}s`;
    return `${Math.floor(s / 60)}m ${s % 60}s`;
  })();

  const httpStatus = typeof execution.outputContext?.statusCode === "number" ? execution.outputContext.statusCode as number : null;
  const httpResponse = (execution.outputContext as unknown as { response?: unknown } | null)?.response;

  return (
    <Card className="overflow-hidden">
      <CardHeader className="space-y-1">
        <div className="flex items-center justify-between gap-2">
          <CardTitle className="text-base">{execution.nodeKey}</CardTitle>
          <Badge variant={st.variant}>{st.icon} {execution.status}</Badge>
        </div>
        <p className="text-xs uppercase tracking-wide text-muted-foreground">{execution.nodeType}</p>
        {execution.lastErrorCode && <p className="text-xs font-medium text-red-600">{execution.lastErrorCode} {execution.lastErrorMessage ? `— ${execution.lastErrorMessage}` : ""}</p>}
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
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

        {/* HTTP request snapshot — safe, redacted at backend */}
        {(() => {
          const out = execution.outputContext as unknown as { request?: { method?: string; url?: string; query?: unknown; headers?: Record<string, string>; body?: unknown } } | null;
          const req = out?.request;
          if (!req || typeof req !== "object") return null;
          const hasAny = req.method || req.url || req.query || req.headers || req.body != null;
          if (!hasAny) return null;
          return (
            <>
              <Separator />
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground" id="http-request-heading">HTTP Request</p>
                <div className="mt-2 space-y-2" aria-labelledby="http-request-heading">
                  {req.method && <p className="text-xs"><span className="text-muted-foreground">Method:</span> <span className="font-mono rounded bg-muted px-1">{String(req.method)}</span></p>}
                  {req.url && <p className="text-xs break-all"><span className="text-muted-foreground">URL:</span> <span className="font-mono rounded bg-muted px-1 break-all">{String(req.url)}</span></p>}
                  {req.query != null && typeof req.query === "object" && Object.keys(req.query as Record<string, unknown>).length > 0 && (
                    <div>
                      <p className="text-xs font-medium text-muted-foreground">Query Parameters</p>
                      <pre className="mt-1 max-h-32 overflow-auto rounded-md border bg-muted/40 p-2 font-mono text-xs">{JSON.stringify(req.query, null, 2)}</pre>
                    </div>
                  )}
                  {req.headers != null && typeof req.headers === "object" && Object.keys(req.headers as Record<string, unknown>).length > 0 && (
                    <div>
                      <p className="text-xs font-medium text-muted-foreground">Headers (redacted)</p>
                      <pre className="mt-1 max-h-32 overflow-auto rounded-md border bg-muted/40 p-2 font-mono text-xs">{JSON.stringify(req.headers, null, 2)}</pre>
                    </div>
                  )}
                  {req.body != null && (
                    <div>
                      <p className="text-xs font-medium text-muted-foreground">Body</p>
                      <pre className="mt-1 max-h-40 overflow-auto whitespace-pre-wrap break-words rounded-md border bg-muted/40 p-2 font-mono text-xs">{typeof req.body === "string" ? req.body : JSON.stringify(req.body, null, 2)}</pre>
                    </div>
                  )}
                  {(req.query == null || Object.keys(req.query as Record<string, unknown>).length === 0) && (req.headers == null || Object.keys(req.headers as Record<string, unknown>).length === 0) && req.body == null && !req.method && !req.url && (
                    <p className="text-xs text-muted-foreground">Request details were not recorded for this execution.</p>
                  )}
                </div>
              </div>
            </>
          );
        })()}

        <Separator />

        <JsonBlock value={execution.inputContext} label="Input" emptyLabel="No input context" />
        <JsonBlock value={execution.outputContext} label="Output" emptyLabel="No output context" />

        {(execution.lastErrorCode || execution.lastErrorMessage) && (
          <>
            <Separator />
            <div>
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Last error</p>
              <p className="mt-1 break-words text-sm font-medium text-red-600">{execution.lastErrorCode ?? "Error"}</p>
              {execution.lastErrorMessage && <p className="mt-1 break-words text-sm text-red-600">{execution.lastErrorMessage}</p>}
            </div>
          </>
        )}

        {execution.status === "SKIPPED" && (
          <p className="rounded-md bg-slate-50 p-2 text-xs text-slate-600 dark:bg-slate-900">This node was skipped — its branch was not taken.</p>
        )}
      </CardContent>
    </Card>
  );
}
