"use client";

import { useState } from "react";
import Link from "next/link";
import { Loader2, Package, CheckCircle, XCircle, AlertTriangle, Clock, Hash, Key, Copy, ExternalLink, Activity } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useWebhookDeliveries, useWebhookDelivery } from "@/lib/hooks/records";

interface Props {
  webhookId: string;
  webhookKey: string;
}

function StatusBadge({ status, failureStage }: { status: string; failureStage?: string | null }) {
  if (status === "SUCCESS") return <Badge className="bg-emerald-50 text-emerald-700 border-emerald-200"><CheckCircle className="h-3 w-3 mr-1" />SUCCESS</Badge>;
  if (status === "FAILED") {
    const stage = failureStage || "UNKNOWN";
    const color = stage === "AUTHENTICATION" ? "bg-red-50 text-red-700 border-red-200" : stage === "VALIDATION" ? "bg-amber-50 text-amber-700 border-amber-200" : "bg-red-50 text-red-700 border-red-200";
    return <Badge variant="outline" className={color}><XCircle className="h-3 w-3 mr-1" />{status} · {stage}</Badge>;
  }
  return <Badge variant="outline"><Clock className="h-3 w-3 mr-1" />{status}</Badge>;
}

export function WebhookDeliveriesPanel({ webhookId, webhookKey }: Props) {
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [selectedDeliveryId, setSelectedDeliveryId] = useState<string | null>(null);

  const { data, isLoading, isError, error } = useWebhookDeliveries(webhookId, {
    status: statusFilter !== "all" ? statusFilter : undefined,
    page,
    size: 20,
  });

  const list = data?.data ?? [];
  const meta = data?.meta;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2 text-sm">
          <Package className="h-4 w-4 text-muted-foreground" />
          <span className="font-medium">Deliveries for</span>
          <span className="font-mono bg-muted px-2 py-0.5 rounded text-xs">{webhookKey}</span>
        </div>
        <div className="flex gap-2">
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger className="w-[160px]"><SelectValue placeholder="All statuses" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All statuses</SelectItem>
              <SelectItem value="SUCCESS">SUCCESS</SelectItem>
              <SelectItem value="FAILED">FAILED</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="rounded-xl border bg-white overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40">
              <TableHead>Received</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>RecordType / Mapping</TableHead>
              <TableHead>Record / Event</TableHead>
              <TableHead>Idempotency</TableHead>
              <TableHead>Payload Hash</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow><TableCell colSpan={6} className="h-32"><div className="flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div></TableCell></TableRow>
            ) : isError ? (
              <TableRow><TableCell colSpan={6} className="h-32 text-center text-sm text-destructive">Failed to load deliveries: {(error as any)?.response?.data?.error?.message || (error as Error).message}</TableCell></TableRow>
            ) : list.length === 0 ? (
              <TableRow><TableCell colSpan={6} className="h-32"><div className="flex flex-col items-center gap-2 py-6 text-center"><Package className="h-6 w-6 text-muted-foreground" /><p className="font-medium">No deliveries yet</p><p className="text-xs text-muted-foreground">Send a webhook request to {webhookKey} to see operational chain.</p></div></TableCell></TableRow>
            ) : (
              list.map((d) => (
                <TableRow key={d.id} className="hover:bg-muted/20 cursor-pointer" onClick={() => setSelectedDeliveryId(d.id)}>
                  <TableCell className="text-xs whitespace-nowrap">{new Date(d.receivedAt).toLocaleString()}</TableCell>
                  <TableCell><StatusBadge status={d.status} failureStage={d.failureStage} /></TableCell>
                  <TableCell className="text-xs">
                    <div className="flex flex-col">
                      <span className="font-medium">{d.recordTypeName || d.recordTypeKey || "—"}</span>
                      <span className="text-muted-foreground font-mono text-[11px]">{d.mappingProfileName ? `${d.mappingProfileName} (${d.mappingMode})` : "Direct"}</span>
                    </div>
                  </TableCell>
                  <TableCell className="text-xs space-y-1">
                    {d.recordId ? <div className="flex items-center gap-1"><span className="font-mono">{d.recordId.slice(0, 8)}…</span><Badge variant="outline" className="text-[10px]">Record</Badge></div> : <span className="text-muted-foreground">—</span>}
                    {d.eventId ? <div className="flex items-center gap-1"><Hash className="h-3 w-3" /><span className="font-mono">{d.eventId.slice(0, 8)}…</span><Badge variant="outline" className="text-[10px]">Event</Badge></div> : d.status === "FAILED" ? <span className="text-amber-700 text-[11px]">{d.errorCode || d.failureStage}</span> : null}
                  </TableCell>
                  <TableCell className="text-xs">
                    {d.idempotencyKey ? <span className="font-mono flex items-center gap-1"><Key className="h-3 w-3" />{d.idempotencyKey.slice(0, 12)}…</span> : <span className="text-muted-foreground">—</span>}
                  </TableCell>
                  <TableCell className="font-mono text-[11px] truncate max-w-[120px]" title={d.payloadHash}>{d.payloadHash.slice(0, 12)}…</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {meta && meta.totalPages > 1 && (
        <div className="flex items-center justify-between px-2">
          <div className="text-sm text-muted-foreground">Page {meta.page + 1} of {meta.totalPages} ({meta.total} deliveries)</div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>Previous</Button>
            <Button variant="outline" size="sm" onClick={() => setPage((p) => p + 1)} disabled={page >= meta.totalPages - 1}>Next</Button>
          </div>
        </div>
      )}

      <DeliveryDetailDialog webhookId={webhookId} deliveryId={selectedDeliveryId} onOpenChange={(open) => { if (!open) setSelectedDeliveryId(null); }} />
    </div>
  );
}

function DeliveryDetailDialog({ webhookId, deliveryId, onOpenChange }: { webhookId: string; deliveryId: string | null; onOpenChange: (open: boolean) => void }) {
  const { data: detail, isLoading, isError } = useWebhookDelivery(webhookId, deliveryId || undefined);
  const open = !!deliveryId;
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2"><Activity className="h-4 w-4" /> Delivery Operational Chain</DialogTitle>
        </DialogHeader>
        {isLoading ? (
          <div className="flex justify-center py-12"><Loader2 className="h-6 w-6 animate-spin" /></div>
        ) : isError || !detail ? (
          <div className="text-sm text-muted-foreground py-8 text-center">Failed to load delivery detail.</div>
        ) : (
          <div className="space-y-4">
            {/* Delivery */}
            <Card className="border-l-4 border-l-indigo-500">
              <CardHeader className="pb-2"><CardTitle className="text-sm flex items-center gap-2"><Package className="h-4 w-4" /> Delivery <Badge variant={detail.delivery.status === "SUCCESS" ? "default" : "destructive"}>{detail.delivery.status}</Badge>{detail.delivery.failureStage && <Badge variant="outline">{detail.delivery.failureStage}</Badge>}</CardTitle></CardHeader>
              <CardContent className="text-xs space-y-1">
                <div>ID: <span className="font-mono">{detail.delivery.id}</span></div>
                <div>Received: {new Date(detail.delivery.receivedAt).toLocaleString()}</div>
                <div>Payload Hash: <span className="font-mono">{detail.delivery.payloadHash}</span></div>
                <div>Idempotency: <span className="font-mono">{detail.idempotency.key || "—"}</span> {detail.idempotency.applied && <Badge variant="secondary" className="text-[10px]">Replay</Badge>}</div>
                {detail.delivery.errorMessage && <div className="text-amber-700">Error: {detail.delivery.errorCode} - {detail.delivery.errorMessage}</div>}
              </CardContent>
            </Card>

            {/* Webhook */}
            <Card><CardHeader className="pb-2"><CardTitle className="text-sm">Webhook</CardTitle></CardHeader><CardContent className="text-xs"><div>Name: {detail.webhook?.name}</div><div>Key: <span className="font-mono">{detail.webhook?.key}</span></div></CardContent></Card>

            {/* RecordType / Mapping */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Card><CardHeader className="pb-2"><CardTitle className="text-sm">Record Type</CardTitle></CardHeader><CardContent className="text-xs">{detail.recordType ? <><div>{detail.recordType.name} ({detail.recordType.key})</div><div className="font-mono">{detail.recordType.id.slice(0,8)}…</div></> : <span className="text-muted-foreground">—</span>}</CardContent></Card>
              <Card><CardHeader className="pb-2"><CardTitle className="text-sm">Mapping</CardTitle></CardHeader><CardContent className="text-xs">{detail.mappingProfile ? <><div>{detail.mappingProfile.name} ({detail.mappingProfile.mode})</div><div className="font-mono">{detail.mappingProfile.key} · {detail.mappingProfile.id.slice(0,8)}…</div></> : <span>Direct</span>}</CardContent></Card>
            </div>

            {/* Record */}
            <Card><CardHeader className="pb-2"><CardTitle className="text-sm">Record</CardTitle></CardHeader><CardContent className="text-xs">{detail.record ? <Link href={`/records/${detail.record.id}`} className="font-mono text-indigo-600 hover:underline flex items-center gap-1">{detail.record.id} <ExternalLink className="h-3 w-3" /></Link> : <span className="text-muted-foreground">No record created (failed before record creation)</span>}</CardContent></Card>

            {/* Event */}
            <Card><CardHeader className="pb-2"><CardTitle className="text-sm">Event — RECORD.RECEIVED</CardTitle></CardHeader><CardContent className="text-xs">{detail.event ? <><div>Event ID: <span className="font-mono">{detail.event.eventId}</span></div><div>{detail.event.entityType}.{detail.event.eventType}</div></> : <span className="text-muted-foreground">No event emitted (failed before publication)</span>}</CardContent></Card>

            {/* Workflow */}
            <Card className={detail.workflow.triggered ? "border-l-4 border-l-emerald-500" : ""}><CardHeader className="pb-2"><CardTitle className="text-sm flex items-center gap-2">Workflow {detail.workflow.triggered ? <CheckCircle className="h-4 w-4 text-emerald-600" /> : <AlertTriangle className="h-4 w-4 text-amber-600" />}{detail.workflow.triggered ? "Matched" : "No matching workflow"}</CardTitle></CardHeader><CardContent className="text-xs space-y-2">{detail.workflow.executions.length === 0 ? <span className="text-muted-foreground">{detail.event ? "No active workflow matched this recordType (or trigger filter did not match). Delivery itself succeeded." : "No workflow check - event not emitted."}</span> : detail.workflow.executions.map((e) => (<div key={e.id} className="flex items-center justify-between border rounded p-2"><div><div>Workflow: {e.workflowName || e.workflowId.slice(0,8)}…</div><div className="font-mono">{e.id.slice(0,8)}… · {e.status}</div></div><Link href={`/workflows/${e.workflowId}/executions/${e.id}`}><Button variant="ghost" size="sm"><ExternalLink className="h-3 w-3 mr-1" />Execution</Button></Link></div>))}</CardContent></Card>

            {/* Failure */}
            {detail.failure.stage && detail.failure.stage !== "SUCCESS" && (
              <Card className="border-destructive/30 bg-destructive/5"><CardHeader className="pb-2"><CardTitle className="text-sm text-destructive">Failure</CardTitle></CardHeader><CardContent className="text-xs"><div>Stage: {detail.failure.stage}</div><div>Code: {detail.failure.code}</div><div>Message: {detail.failure.message}</div></CardContent></Card>
            )}

            {/* Security note */}
            <p className="text-[11px] text-muted-foreground">Secrets, API keys, HMAC signatures, and raw payloads are never stored or displayed. Payload is represented by hash only.</p>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
