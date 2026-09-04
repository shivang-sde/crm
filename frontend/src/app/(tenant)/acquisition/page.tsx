"use client";

import { useState } from "react";
import Link from "next/link";
import {
  Pencil,
  Plus,
  Power,
  Trash2,
  Webhook,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Clock,
  Settings,
  FileSearch,
  Activity,
  Copy,
  FileSpreadsheet,
  ExternalLink,
} from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  useAcquisitionConfigs,
  useCreateAcquisitionConfig,
  useDeleteAcquisitionConfig,
  useUpdateAcquisitionConfig,
  useLeadIngestionMappings,
  useLeadIngestionEvents,
} from "@/lib/hooks/acquisition";
import { AcquisitionConfigDialog } from "@/components/acquisition/AcquisitionConfigDialog";
import { usePermissions } from "@/lib/hooks/usePermissions";
import {
  LeadIngestionConfigResponse,
  LeadIngestionConfigCreateRequest,
  LeadIngestionTransportType,
} from "@/types/acquisition";

const transportLabels: Record<LeadIngestionTransportType, string> = {
  WEBHOOK: "Webhook",
  API: "API",
  FORM: "Form",
  CONNECTOR: "Connector",
  POLLING: "Polling",
  IMPORT: "Import",
};

const transportAvailability: Record<LeadIngestionTransportType, { available: boolean; note: string }> = {
  WEBHOOK: { available: true, note: "Webhook — available now" },
  IMPORT: { available: true, note: "CSV import — available now" },
  FORM: { available: true, note: "Public form — available now" },
  API: { available: true, note: "Direct API — available now" },
  POLLING: { available: true, note: "API Polling — available now" },
  CONNECTOR: { available: false, note: "Coming soon" },
};

function SourceCard({
  config,
  onEdit,
  onToggle,
  onDelete,
  canEdit,
  canDelete,
}: {
  config: LeadIngestionConfigResponse;
  onEdit: (c: LeadIngestionConfigResponse) => void;
  onToggle: (c: LeadIngestionConfigResponse) => void;
  onDelete: (id: string) => void;
  canEdit: boolean;
  canDelete: boolean;
}) {
  const mappingsQ = useLeadIngestionMappings(config.id);
  const eventsQ = useLeadIngestionEvents(config.id, { page: 0, size: 3 });
  const mappings = mappingsQ.data?.data ?? [];
  const activeMappings = mappings.filter((m) => m.active).length;
  const events = eventsQ.data?.data ?? [];
  const lastEvent = events[0];

  const isSupported = transportAvailability[config.transportType]?.available ?? false;
  const needsConfig = activeMappings === 0;
  const health: { label: string; variant: "default" | "secondary" | "destructive" | "outline"; icon: typeof CheckCircle2 } = !isSupported
    ? { label: "Unsupported", variant: "destructive", icon: XCircle }
    : !config.active
      ? { label: "Inactive", variant: "secondary", icon: Clock }
      : needsConfig
        ? { label: "Needs configuration", variant: "outline", icon: AlertTriangle }
        : { label: "Active", variant: "default", icon: CheckCircle2 };

  const HealthIcon = health.icon;

  return (
    <Card className="overflow-hidden">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <CardTitle className="truncate text-base flex items-center gap-2">
              {config.transportType === "IMPORT" || config.transportType === "POLLING" || config.transportType === "FORM" ? (
                <FileSpreadsheet className="h-4 w-4 text-muted-foreground" />
              ) : (
                <Webhook className="h-4 w-4 text-muted-foreground" />
              )}
              {config.name}
            </CardTitle>
            <CardDescription className="truncate">
              {transportLabels[config.transportType] ?? config.transportType}
              {!isSupported && " · Coming soon"}
              {config.publicKey && isSupported && ` · ${config.inboundPath ?? `/api/v1/public/acquisition/${config.publicKey}`}`}
            </CardDescription>
          </div>
          <Badge variant={health.variant} className="shrink-0">
            <HealthIcon className="mr-1 h-3 w-3" />
            {health.label}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid grid-cols-3 gap-2 text-center">
          <div className="rounded-md border bg-muted/20 p-2">
            <p className="text-xs text-muted-foreground">Mappings</p>
            <p className="text-sm font-semibold">
              {mappingsQ.isLoading ? <Skeleton className="h-4 w-8 mx-auto" /> : `${activeMappings}/${mappings.length}`}
            </p>
            <p className="text-[10px] text-muted-foreground">active/total</p>
          </div>
          <div className="rounded-md border bg-muted/20 p-2">
            <p className="text-xs text-muted-foreground">Recent events</p>
            <p className="text-sm font-semibold">
              {eventsQ.isLoading ? <Skeleton className="h-4 w-8 mx-auto" /> : events.length ? `${events.length}` : "—"}
            </p>
            <p className="text-[10px] text-muted-foreground">last 3</p>
          </div>
          <div className="rounded-md border bg-muted/20 p-2">
            <p className="text-xs text-muted-foreground">Last event</p>
            <p className="text-xs font-medium truncate">
              {lastEvent ? new Date(lastEvent.receivedAt).toLocaleDateString() : "—"}
            </p>
            <p className="text-[10px] text-muted-foreground truncate">
              {lastEvent ? lastEvent.status : "No events yet"}
            </p>
          </div>
        </div>

        {config.transportType === "WEBHOOK" && isSupported && config.publicKey && (
          <div className="rounded-md bg-muted/30 border px-2 py-1 flex items-center gap-2">
            <code className="text-xs truncate flex-1">{config.inboundPath ?? `/api/v1/public/acquisition/${config.publicKey}`}</code>
            <Button
              variant="ghost"
              size="icon"
              className="h-6 w-6 shrink-0"
              onClick={() => {
                const url = config.inboundPath ?? `/api/v1/public/acquisition/${config.publicKey}`;
                navigator.clipboard.writeText(window.location.origin + url);
                toast.success("Endpoint copied");
              }}
              aria-label="Copy endpoint"
            >
              <Copy className="h-3 w-3" />
            </Button>
          </div>
        )}
        {config.transportType === "IMPORT" && isSupported && (
          <div className="rounded-md bg-muted/30 border px-2 py-1">
            <p className="text-xs text-muted-foreground flex items-center gap-1">
              <FileSearch className="h-3 w-3" /> CSV file import · Upload → Map → Preview → Import
            </p>
          </div>
        )}
        {config.transportType === "FORM" && isSupported && config.publicKey && (
          <div className="rounded-md bg-muted/30 border px-2 py-1 flex items-center gap-2">
            <code className="text-xs truncate flex-1">{`${typeof window !== "undefined" ? window.location.origin : ""}/forms/public/${config.publicKey}`}</code>
            <Button
              variant="ghost"
              size="icon"
              className="h-6 w-6 shrink-0"
              onClick={() => {
                const url = `${window.location.origin}/forms/public/${config.publicKey}`;
                navigator.clipboard.writeText(url);
                toast.success("Form URL copied");
              }}
              aria-label="Copy form URL"
            >
              <Copy className="h-3 w-3" />
            </Button>
          </div>
        )}
        {config.transportType === "API" && isSupported && config.publicKey && (
          <div className="rounded-md bg-muted/30 border px-2 py-1 flex items-center gap-2">
            <code className="text-xs truncate flex-1">{config.inboundPath ?? `/api/v1/public/direct/${config.publicKey}`}</code>
            <Button
              variant="ghost"
              size="icon"
              className="h-6 w-6 shrink-0"
              onClick={() => {
                const url = config.inboundPath ?? `/api/v1/public/direct/${config.publicKey}`;
                navigator.clipboard.writeText(window.location.origin + url);
                toast.success("Direct API endpoint copied");
              }}
              aria-label="Copy direct API endpoint"
            >
              <Copy className="h-3 w-3" />
            </Button>
          </div>
        )}
        {config.transportType === "POLLING" && isSupported && (
          <div className="rounded-md bg-muted/30 border px-2 py-1">
            <p className="text-xs text-muted-foreground flex items-center gap-1">
              <Activity className="h-3 w-3" /> Polling · CRM → external API → records → pipeline
            </p>
          </div>
        )}

        {!isSupported && (
          <p className="text-xs text-amber-600 dark:text-amber-400 flex items-center gap-1">
            <AlertTriangle className="h-3 w-3" /> This transport is not yet available — configuration is view-only.
          </p>
        )}

        <div className="flex flex-wrap gap-2">
          <Link href={`/acquisition/configs/${config.id}`}>
            <Button variant="default" size="sm">
              <Settings className="mr-1 h-3 w-3" /> Open
            </Button>
          </Link>
          {config.transportType === "IMPORT" ? (
            <Link href={`/acquisition/configs/${config.id}/import`}>
              <Button variant="outline" size="sm">
                <FileSearch className="mr-1 h-3 w-3" /> Import CSV
              </Button>
            </Link>
          ) : config.transportType === "FORM" ? (
            <>
              <Link href={`/forms/public/${config.publicKey}`} target="_blank">
                <Button variant="outline" size="sm">
                  <ExternalLink className="mr-1 h-3 w-3" /> Form
                </Button>
              </Link>
              <Link href={`/acquisition/configs/${config.id}/mappings`}>
                <Button variant="outline" size="sm">
                  <FileSearch className="mr-1 h-3 w-3" /> Mappings
                </Button>
              </Link>
            </>
          ) : (
            <Link href={`/acquisition/configs/${config.id}/mappings`}>
              <Button variant="outline" size="sm">
                <FileSearch className="mr-1 h-3 w-3" /> Mappings
              </Button>
            </Link>
          )}
          <Link href={`/acquisition/configs/${config.id}/events`}>
            <Button variant="outline" size="sm">
              <Activity className="mr-1 h-3 w-3" /> Events
            </Button>
          </Link>
          {canEdit && (
            <>
              <Button variant="outline" size="sm" onClick={() => onEdit(config)}>
                <Pencil className="mr-1 h-3 w-3" /> Edit
              </Button>
              <Button variant="outline" size="sm" onClick={() => onToggle(config)} disabled={!isSupported}>
                <Power className="mr-1 h-3 w-3" /> {config.active ? "Deactivate" : "Activate"}
              </Button>
            </>
          )}
          {canDelete && (
            <Button variant="destructive" size="sm" onClick={() => onDelete(config.id)}>
              <Trash2 className="mr-1 h-3 w-3" /> Delete
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

export default function AcquisitionPage() {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<LeadIngestionConfigResponse | null>(null);
  const [deleteId, setDeleteId] = useState<string | null>(null);

  const { canViewAcquisition, canEditAcquisition, canDeleteAcquisition } =
    usePermissions();

  const { data, isLoading, isError, refetch } = useAcquisitionConfigs();
  const createConfig = useCreateAcquisitionConfig();
  const updateConfig = useUpdateAcquisitionConfig();
  const deleteConfig = useDeleteAcquisitionConfig();

  const configs = data?.data ?? [];
  const activeCount = configs.filter((c) => c.active).length;
  const inactiveCount = configs.length - activeCount;
  const webhookCount = configs.filter((c) => c.transportType === "WEBHOOK").length;
  const importCount = configs.filter((c) => c.transportType === "IMPORT").length;
  const formCount = configs.filter((c) => c.transportType === "FORM").length;
  const apiCount = configs.filter((c) => c.transportType === "API").length;
  const pollingCount = configs.filter((c) => c.transportType === "POLLING").length;

  if (!canViewAcquisition) {
    return (
      <div className="space-y-6 p-6">
        <h1 className="text-2xl font-semibold">Lead Acquisition</h1>
        <p className="text-sm text-muted-foreground">
          You do not have permission to view acquisition configurations.
        </p>
      </div>
    );
  }

  const handleSave = async (values: LeadIngestionConfigCreateRequest) => {
    const avail = transportAvailability[values.transportType]?.available;
    if (!avail) {
      toast.error("This transport is not yet available.");
      return;
    }
    try {
      if (editing?.id) {
        await updateConfig.mutateAsync({ id: editing.id, data: values });
        toast.success("Source updated successfully");
      } else {
        await createConfig.mutateAsync(values);
        if (values.transportType === "IMPORT") {
          toast.success("CSV import source created — next: upload CSV and map columns");
        } else if (values.transportType === "FORM") {
          toast.success("Form source created — next: configure mapping and share form URL");
        } else if (values.transportType === "API") {
          toast.success("Direct API source created — copy endpoint and send JSON");
        } else if (values.transportType === "POLLING") {
          toast.success("Polling source created — configure endpoint and schedule");
        } else {
          toast.success("Webhook source created — next: configure mapping and test");
        }
      }
      setOpen(false);
      setEditing(null);
    } catch (e: any) {
      const msg = e?.response?.data?.error?.message ?? "Failed to save source";
      toast.error(msg);
    }
  };

  const handleToggle = async (config: LeadIngestionConfigResponse) => {
    if (!transportAvailability[config.transportType]?.available) {
      toast.error("Unsupported transport cannot be activated");
      return;
    }
    // check needs config: warn but allow backend to validate
    try {
      await updateConfig.mutateAsync({
        id: config.id,
        data: {
          name: config.name,
          transportType: config.transportType,
          active: !config.active,
          settings: config.settings ?? {},
        },
      });
      toast.success(config.active ? "Source deactivated" : "Source activated");
    } catch (e: any) {
      toast.error(e?.response?.data?.error?.message ?? "Failed to update source");
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deleteId) return;
    try {
      await deleteConfig.mutateAsync(deleteId);
      toast.success("Source deleted — existing events and leads are preserved");
      setDeleteId(null);
    } catch {
      toast.error("Failed to delete source");
    }
  };

  const isSubmitting = createConfig.isPending || updateConfig.isPending;

  return (
    <div className="space-y-6 p-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Lead Sources</h1>
          <p className="text-sm text-muted-foreground max-w-2xl">
            Where do your leads come from? Connect a source, map its information to CRM fields, and activate.
          </p>
        </div>

        {canEditAcquisition && (
          <Button
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            <Plus className="mr-2 h-4 w-4" /> Create Source
          </Button>
        )}
      </div>

      {/* Overview stats */}
      <div className="grid gap-3 md:grid-cols-4">
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Total sources</CardDescription>
            <CardTitle className="text-2xl">{isLoading ? <Skeleton className="h-7 w-12" /> : configs.length}</CardTitle>
          </CardHeader>
          <CardContent className="text-xs text-muted-foreground">
            {webhookCount} Webhook · {importCount} Import · {formCount} Form · {apiCount} Direct API · {pollingCount} Polling · {configs.length - webhookCount - importCount - formCount - apiCount - pollingCount} other
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Active</CardDescription>
            <CardTitle className="text-2xl flex items-center gap-2">
              {isLoading ? <Skeleton className="h-7 w-12" /> : <><CheckCircle2 className="h-5 w-5 text-green-600" /> {activeCount}</>}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-xs text-muted-foreground">Receiving leads</CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Inactive</CardDescription>
            <CardTitle className="text-2xl flex items-center gap-2">
              {isLoading ? <Skeleton className="h-7 w-12" /> : <><Clock className="h-5 w-5 text-amber-600" /> {inactiveCount}</>}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-xs text-muted-foreground">Paused or needs activation</CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Transports</CardDescription>
            <CardTitle className="text-sm">Webhook ✓ · Import ✓ · Form ✓ · Direct API ✓ · Polling ✓</CardTitle>
          </CardHeader>
          <CardContent className="text-xs text-muted-foreground">Connector — coming later (LEAD-ING-8)</CardContent>
        </Card>
      </div>

      {/* Transport availability */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">How leads arrive</CardTitle>
          <CardDescription>Webhook and CSV Import are fully supported. Other transports are shown for roadmap clarity and cannot be activated.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid gap-2 md:grid-cols-3">
            {(Object.entries(transportAvailability) as [LeadIngestionTransportType, { available: boolean; note: string }][]).map(([k, v]) => (
              <div
                key={k}
                className={`rounded-lg border p-3 flex flex-col gap-1 ${v.available ? "bg-green-50 border-green-200 dark:bg-green-950/20" : "bg-muted/30 opacity-75"}`}
              >
                <div className="flex items-center gap-2">
                  <Badge variant={v.available ? "default" : "secondary"}>{transportLabels[k]}</Badge>
                  {v.available ? (
                    <CheckCircle2 className="h-3 w-3 text-green-600" />
                  ) : (
                    <Clock className="h-3 w-3 text-muted-foreground" />
                  )}
                </div>
                <p className="text-xs text-muted-foreground">{v.note}</p>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Sources */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Sources</CardTitle>
              <CardDescription>Each source has its own mapping, test, and event history. Health is derived from recent outcomes.</CardDescription>
            </div>
            <Badge variant="outline">{configs.length} total</Badge>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {[1, 2, 3].map((i) => (
                <Card key={i} className="p-4 space-y-2">
                  <Skeleton className="h-5 w-32" />
                  <Skeleton className="h-4 w-full" />
                  <Skeleton className="h-8 w-24" />
                </Card>
              ))}
            </div>
          ) : isError ? (
            <div className="rounded-md border border-red-200 bg-red-50 p-4 dark:border-red-900 dark:bg-red-950/30">
              <p className="text-sm font-medium text-red-700 dark:text-red-300">Unable to load sources</p>
              <p className="text-xs text-red-600 dark:text-red-400">The acquisition service could not be reached.</p>
              <Button variant="outline" size="sm" className="mt-2" onClick={() => refetch()}>
                Try again
              </Button>
            </div>
          ) : configs.length === 0 ? (
            <div className="rounded-lg border border-dashed p-8 text-center">
              <div className="mx-auto max-w-md space-y-3">
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-muted">
                  <Webhook className="h-6 w-6 text-muted-foreground" />
                </div>
                <h3 className="font-semibold">No lead sources yet</h3>
                <p className="text-sm text-muted-foreground">
                  Create a Webhook to receive live leads or a CSV Import to bulk upload. You’ll then map columns/fields, test, and activate.
                </p>
                {canEditAcquisition && (
                  <Button onClick={() => setOpen(true)}>
                    <Plus className="mr-2 h-4 w-4" /> Create Source
                  </Button>
                )}
              </div>
            </div>
          ) : (
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {configs.map((config) => (
                <SourceCard
                  key={config.id}
                  config={config}
                  onEdit={(c) => {
                    setEditing(c);
                    setOpen(true);
                  }}
                  onToggle={handleToggle}
                  onDelete={(id) => setDeleteId(id)}
                  canEdit={canEditAcquisition}
                  canDelete={canDeleteAcquisition}
                />
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <AcquisitionConfigDialog
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (!next) setEditing(null);
        }}
        editing={editing}
        onSubmit={handleSave}
        isSubmitting={isSubmitting}
      />

      <AlertDialog open={!!deleteId} onOpenChange={(o) => !o && setDeleteId(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete source?</AlertDialogTitle>
            <AlertDialogDescription>
              This will stop the source from accepting new leads. Existing ingestion events and leads will not be deleted. This cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleDeleteConfirm} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              Delete source
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
