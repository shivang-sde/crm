"use client";

import { useState } from "react";
import { Plus, Pencil, Trash2, Loader2, SearchX, Key, Copy, Check, Database, Settings2, Shield, RefreshCw, Package } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { useWebhooks, useCreateWebhook, useUpdateWebhook, useDeleteWebhook, useRotateWebhookSecret, useRecordTypes } from "@/lib/hooks/records";
import { RecordWebhookResponse } from "@/types/records";
import { WebhookDialog } from "./WebhookDialog";
import { WebhookDeliveriesPanel } from "./WebhookDeliveriesPanel";
import { toast } from "sonner";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";

export function WebhooksAdmin() {
  const [page] = useState(0);
  const [filter, setFilter] = useState("");
  const [recordTypeFilter, setRecordTypeFilter] = useState<string>("all");
  const [copied, setCopied] = useState<string | null>(null);

  const { data: typesData } = useRecordTypes(0, 100);
  const types = typesData?.data ?? [];
  const typeMap = new Map(types.map((t) => [t.id, t]));

  const { data, isLoading, isError, error } = useWebhooks({ page, size: 50, recordTypeId: recordTypeFilter !== "all" ? recordTypeFilter : undefined });
  const createMutation = useCreateWebhook();
  const updateMutation = useUpdateWebhook();
  const deleteMutation = useDeleteWebhook();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<RecordWebhookResponse | null>(null);
  const [toDelete, setToDelete] = useState<RecordWebhookResponse | null>(null);
  const [rotateResult, setRotateResult] = useState<{ secret: string; webhookName: string; authMode: string } | null>(null);
  const rotateMutation = useRotateWebhookSecret();
  const [deliveriesWebhook, setDeliveriesWebhook] = useState<RecordWebhookResponse | null>(null);

  const list = data?.data ?? [];
  const filtered = filter.trim()
    ? list.filter((w) => w.name.toLowerCase().includes(filter.toLowerCase()) || w.webhookKey.toLowerCase().includes(filter.toLowerCase()))
    : list;

  function openCreate() {
    setEditing(null);
    setDialogOpen(true);
  }
  function openEdit(w: RecordWebhookResponse) {
    setEditing(w);
    setDialogOpen(true);
  }
  function handleSubmit(data: any) {
    if (editing) {
      updateMutation.mutate(
        { id: editing.id, data: { name: data.name, description: data.description, isActive: data.isActive, mappingProfileId: data.mappingProfileId, authMode: data.authMode } as any },
        { onSuccess: () => setDialogOpen(false) }
      );
    } else {
      createMutation.mutate(data, { onSuccess: () => setDialogOpen(false) });
    }
  }
  function handleRotate(w: RecordWebhookResponse) {
    if (w.authMode === "NONE") {
      toast.error("Cannot rotate secret for NONE auth mode. Change mode first.");
      return;
    }
    rotateMutation.mutate(w.id, {
      onSuccess: (res: any) => {
        // backend returns {secret, webhook} inside data
        const secret = res?.secret || (res as any)?.data?.secret || "";
        setRotateResult({ secret, webhookName: w.name, authMode: w.authMode });
      },
    });
  }
  function copyEndpoint(path: string) {
    navigator.clipboard.writeText(path);
    setCopied(path);
    toast.success("Endpoint copied");
    setTimeout(() => setCopied(null), 1500);
  }

  const isPending = createMutation.isPending || updateMutation.isPending;

  if (isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }
  if (isError) {
    return (
      <div className="rounded-lg border border-destructive/20 bg-destructive/5 p-6 text-center">
        <p className="text-sm text-destructive">Failed to load webhooks.</p>
        <p className="text-xs text-muted-foreground mt-1">{(error as any)?.response?.data?.error?.message || (error as Error)?.message || "Unknown error"}</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex gap-2 flex-1 max-w-2xl">
          <div className="relative flex-1 max-w-sm">
            <Input placeholder="Search by name or key..." value={filter} onChange={(e) => setFilter(e.target.value)} className="pl-9" />
            <SearchX className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground opacity-50" />
          </div>
          <Select value={recordTypeFilter} onValueChange={setRecordTypeFilter}>
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder="All Record Types" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Record Types</SelectItem>
              {types.map((t) => (
                <SelectItem key={t.id} value={t.id}>
                  {t.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Button onClick={openCreate} className="w-full sm:w-auto">
          <Plus className="mr-2 h-4 w-4" /> New Webhook
        </Button>
      </div>

      <div className="rounded-xl border bg-white overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40">
              <TableHead>Name</TableHead>
              <TableHead>Key / Endpoint</TableHead>
              <TableHead>Record Type</TableHead>
              <TableHead>Mapping</TableHead>
              <TableHead>Auth</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="w-[200px] text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filtered.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="h-64">
                  <div className="flex flex-col items-center justify-center gap-3 py-6 text-center">
                    <div className="rounded-full bg-muted p-4">
                      <Settings2 className="h-8 w-8 text-muted-foreground" />
                    </div>
                    <div>
                      <p className="font-medium">No Webhooks yet</p>
                      <p className="text-sm text-muted-foreground max-w-sm">Create an incoming webhook to define where external payloads will map to a Record Type.</p>
                    </div>
                    <Button onClick={openCreate} size="sm">
                      <Plus className="mr-2 h-4 w-4" /> Create Webhook
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((w) => {
                const rt = typeMap.get(w.recordTypeId);
                return (
                  <TableRow key={w.id} className="hover:bg-muted/20">
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <div className="h-8 w-8 rounded-lg bg-indigo-50 flex items-center justify-center">
                          <Key className="h-4 w-4 text-indigo-600" />
                        </div>
                        <div>
                          <p className="font-medium leading-none">{w.name}</p>
                          {w.description && <p className="text-xs text-muted-foreground line-clamp-1 max-w-[28ch]">{w.description}</p>}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="space-y-1">
                        <p className="font-mono text-xs">{w.webhookKey}</p>
                        <div className="flex items-center gap-1 text-xs">
                          <span className="font-mono bg-muted px-1.5 py-0.5 rounded text-[11px] truncate max-w-[180px]">{w.endpointPath}</span>
                          <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => copyEndpoint(w.endpointPath)}>
                            {copied === w.endpointPath ? <Check className="h-3 w-3 text-emerald-600" /> : <Copy className="h-3 w-3" />}
                          </Button>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-1">
                        <Database className="h-3 w-3 text-muted-foreground" />
                        <span className="text-sm">{w.recordTypeName || rt?.name || "—"}</span>
                        {w.recordTypeKey && <span className="font-mono text-xs text-muted-foreground">({w.recordTypeKey})</span>}
                      </div>
                    </TableCell>
                    <TableCell>
                      {w.mappingProfileId ? (
                        <div className="text-xs">
                          <p className="font-medium">{w.mappingName || w.mappingKey}</p>
                          <p className="text-muted-foreground font-mono">{w.mappingKey} · {w.mappingMode}</p>
                        </div>
                      ) : (
                        <Badge variant="outline">Direct</Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge variant={w.authMode === "NONE" ? "outline" : w.authMode === "API_KEY" ? "secondary" : "default"} className="text-xs">
                        <Shield className="h-3 w-3 mr-1" />
                        {w.authMode}
                      </Badge>
                    </TableCell>
                    <TableCell>{w.isActive ? <Badge className="bg-emerald-50 text-emerald-700 border-emerald-200">Active</Badge> : <Badge variant="outline">Inactive</Badge>}</TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" title="View deliveries (operational debugging)" onClick={() => setDeliveriesWebhook(w)}>
                          <Package className="h-4 w-4" />
                        </Button>
                        {w.authMode !== "NONE" && (
                          <Button variant="ghost" size="icon" title="Rotate secret" onClick={() => handleRotate(w)} disabled={rotateMutation.isPending}>
                            <RefreshCw className="h-4 w-4" />
                          </Button>
                        )}
                        <Button variant="ghost" size="icon" onClick={() => openEdit(w)} aria-label="Edit">
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button variant="ghost" size="icon" className="text-destructive hover:text-destructive" onClick={() => setToDelete(w)} aria-label="Delete">
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      </div>

      <WebhookDialog open={dialogOpen} onOpenChange={setDialogOpen} editing={editing} onSubmit={handleSubmit} isPending={isPending} />

      <Dialog open={!!rotateResult} onOpenChange={() => setRotateResult(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Secret rotated – copy now</DialogTitle>
            <DialogDescription>
              Secret for <strong>{rotateResult?.webhookName}</strong> ({rotateResult?.authMode}) is shown once. Store securely; it will not be shown again.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <div className="flex gap-2">
              <Input value={rotateResult?.secret || ""} readOnly className="font-mono text-xs" />
              <Button
                variant="outline"
                size="icon"
                onClick={() => {
                  if (rotateResult?.secret) {
                    navigator.clipboard.writeText(rotateResult.secret);
                    toast.success("Secret copied");
                  }
                }}
              >
                <Copy className="h-4 w-4" />
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">
              {rotateResult?.authMode === "API_KEY" ? "Send as X-Webhook-API-Key" : "Use as HMAC key for X-Webhook-Signature = HMAC_SHA256(secret, rawBody) hex"}
            </p>
          </div>
        </DialogContent>
      </Dialog>

      <AlertDialog open={!!toDelete} onOpenChange={() => setToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Archive webhook?</AlertDialogTitle>
            <AlertDialogDescription>
              This will archive <strong>{toDelete?.name}</strong> (<span className="font-mono">{toDelete?.webhookKey}</span>). It will be hidden from ingestion and preserved for history (soft-delete).
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction className="bg-destructive hover:bg-destructive/90" onClick={() => toDelete && deleteMutation.mutate(toDelete.id, { onSuccess: () => setToDelete(null) })}>
              Archive
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog open={!!deliveriesWebhook} onOpenChange={(open) => { if (!open) setDeliveriesWebhook(null); }}>
        <DialogContent className="max-w-5xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Webhook Deliveries – {deliveriesWebhook?.name}</DialogTitle>
            <DialogDescription className="font-mono text-xs">{deliveriesWebhook?.endpointPath} · key {deliveriesWebhook?.webhookKey}</DialogDescription>
          </DialogHeader>
          {deliveriesWebhook && <WebhookDeliveriesPanel webhookId={deliveriesWebhook.id} webhookKey={deliveriesWebhook.webhookKey} />}
        </DialogContent>
      </Dialog>
    </div>
  );
}
