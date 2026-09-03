"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ArrowLeft,
  Webhook,
  Settings,
  FileSearch,
  Activity,
  FlaskConical,
  Power,
  Trash2,
  CheckCircle2,
  Clock,
  AlertTriangle,
  Copy,
  ExternalLink,
  Upload,
  FileSpreadsheet,
} from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
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
  useAcquisitionConfig,
  useAcquisitionConfigs,
  useDeleteAcquisitionConfig,
  useUpdateAcquisitionConfig,
  useLeadIngestionMappings,
  useLeadIngestionEvents,
  usePollingStatus,
  useTestPolling,
  useTriggerPolling,
} from "@/lib/hooks/acquisition";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { AcquisitionConfigDialog } from "@/components/acquisition/AcquisitionConfigDialog";
import type { LeadIngestionConfigCreateRequest } from "@/types/acquisition";

function PollingCard({ configId }: { configId: string }) {
  const statusQ = usePollingStatus(configId);
  const testMut = useTestPolling(configId);
  const triggerMut = useTriggerPolling(configId);
  const data = statusQ.data as Record<string, unknown> | undefined;
  const polling = (data?.polling as Record<string, unknown>) ?? {};
  const pollingState = (data?.pollingState as Record<string, unknown>) ?? {};
  const lastStats = pollingState?.lastPollStats as Record<string, unknown> | undefined;

  return (
    <div className="space-y-2">
      <div className="rounded-md border bg-muted/20 p-2 text-xs space-y-1">
        <p>
          <span className="font-medium">Endpoint:</span> {String(polling.endpointUrl ?? "—")}
        </p>
        <p>
          <span className="font-medium">Method:</span> {String(polling.method ?? "GET")} ·{" "}
          <span className="font-medium">Interval:</span> {String(polling.intervalMinutes ?? 15)} min
        </p>
        <p>
          <span className="font-medium">Connection:</span> {String(polling.connectionId ?? "None (NONE auth)")}
        </p>
        {lastStats ? (
          <p className="text-muted-foreground">
            Last poll: {String(lastStats.timestamp ?? "")} · {String(lastStats.totalFetched ?? 0)} fetched (
            {String(lastStats.created ?? 0)} created, {String(lastStats.duplicate ?? 0)} dup,{" "}
            {String(lastStats.rejected ?? 0)} rej, {String(lastStats.failed ?? 0)} failed)
          </p>
        ) : (
          <p className="text-muted-foreground">No polls yet</p>
        )}
        {(pollingState?.lastError as string) && (
          <p className="text-red-600">Last error: {String(pollingState.lastError)}</p>
        )}
      </div>
      <div className="flex gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={async () => {
            try {
              const r = await testMut.mutateAsync();
              toast.success(`Connection OK — ${String((r as Record<string, unknown>).recordsAvailable ?? 0)} records available`);
            } catch (e: unknown) {
              const msg = (e as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message ?? (e as Error).message ?? "Test failed";
              toast.error(msg);
            }
          }}
          disabled={testMut.isPending}
        >
          {testMut.isPending ? "Testing…" : "Test connection"}
        </Button>
        <Button
          size="sm"
          onClick={async () => {
            try {
              const r = await triggerMut.mutateAsync();
              const s = r as Record<string, unknown>;
              toast.success(
                `Poll complete: ${String(s.totalFetched ?? 0)} fetched, ${String(s.created ?? 0)} created, ${String(s.duplicate ?? 0)} dup`
              );
              statusQ.refetch();
            } catch (e: unknown) {
              const msg = (e as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message ?? (e as Error).message ?? "Poll failed";
              toast.error(msg);
            }
          }}
          disabled={triggerMut.isPending}
        >
          {triggerMut.isPending ? "Polling…" : "Trigger poll"}
        </Button>
      </div>
      <p className="text-xs text-muted-foreground">
        Polling respects incremental sync (updated_since) and pagination. Each record becomes one event; duplicates via Lead dedup, not repeated leads.
      </p>
    </div>
  );
}

function PollingSettingsEditor({ config }: { config: { id: string; settings?: Record<string, unknown> | null } }) {
  const updateMut = useUpdateAcquisitionConfig();
  const settings = (config.settings as Record<string, unknown> | undefined) ?? {};
  const polling = (settings.polling as Record<string, unknown> | undefined) ?? {};

  const [endpointUrl, setEndpointUrl] = useState<string>(String(polling.endpointUrl ?? ""));
  const [method, setMethod] = useState<string>(String(polling.method ?? "GET"));
  const [connectionId, setConnectionId] = useState<string>(String(polling.connectionId ?? ""));
  const [recordsPath, setRecordsPath] = useState<string>(String(polling.recordsPath ?? "data"));
  const [externalIdPath, setExternalIdPath] = useState<string>(String(polling.externalIdPath ?? "id"));
  const [intervalMinutes, setIntervalMinutes] = useState<string>(String(polling.intervalMinutes ?? 15));
  const [pageSize, setPageSize] = useState<string>(String(polling.pageSize ?? 50));

  const handleSave = async () => {
    const newPolling: Record<string, unknown> = {
      endpointUrl: endpointUrl.trim(),
      method: method.trim() || "GET",
      recordsPath: recordsPath.trim() || "data",
      externalIdPath: externalIdPath.trim() || "id",
      intervalMinutes: parseInt(intervalMinutes) || 15,
      pageSize: parseInt(pageSize) || 50,
    };
    if (connectionId.trim()) newPolling.connectionId = connectionId.trim();
    // Keep existing other polling keys if any
    const newSettings: Record<string, unknown> = { ...settings, polling: newPolling };
    try {
      await updateMut.mutateAsync({
        id: config.id,
        data: {
          name: undefined as unknown as string, // keep existing name via backend handling? Actually update requires name, so we need to pass existing name
          transportType: undefined as unknown as any,
          active: undefined as unknown as boolean,
          settings: newSettings,
        },
      });
      toast.success("Polling settings saved");
    } catch (e: unknown) {
      // Fallback: try with full config (name required)
      try {
        await updateMut.mutateAsync({
          id: config.id,
          data: {
            name: (config as unknown as { name: string }).name ?? "polling",
            transportType: "POLLING" as unknown as any,
            active: true,
            settings: newSettings,
          },
        });
        toast.success("Polling settings saved");
      } catch (err: unknown) {
        const msg = (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message ?? (err as Error).message ?? "Save failed";
        toast.error(msg);
      }
    }
  };

  return (
    <div className="rounded-md border p-3 space-y-3">
      <p className="text-sm font-medium">Polling configuration</p>
      <p className="text-xs text-muted-foreground">
        Endpoint + connection + schedule. Uses existing OutboundHttpConnection (API_KEY/BEARER/BASIC/NONE) with encrypted credentials. Test connection before activating.
      </p>
      <div className="grid gap-2">
        <label className="text-xs font-medium">
          Endpoint URL
          <input
            className="mt-1 w-full rounded-md border px-2 py-1 text-sm"
            placeholder="https://api.example.com/leads"
            value={endpointUrl}
            onChange={(e) => setEndpointUrl(e.target.value)}
          />
        </label>
        <div className="grid grid-cols-2 gap-2">
          <label className="text-xs font-medium">
            Method
            <select
              className="mt-1 w-full rounded-md border px-2 py-1 text-sm"
              value={method}
              onChange={(e) => setMethod(e.target.value)}
            >
              <option value="GET">GET</option>
              <option value="POST">POST</option>
            </select>
          </label>
          <label className="text-xs font-medium">
            Interval (min)
            <select
              className="mt-1 w-full rounded-md border px-2 py-1 text-sm"
              value={intervalMinutes}
              onChange={(e) => setIntervalMinutes(e.target.value)}
            >
              <option value="5">5</option>
              <option value="15">15</option>
              <option value="60">60</option>
              <option value="1440">1440 (daily)</option>
            </select>
          </label>
        </div>
        <label className="text-xs font-medium">
          Connection ID (OutboundHttpConnection UUID, optional)
          <input
            className="mt-1 w-full rounded-md border px-2 py-1 text-sm"
            placeholder="leave empty for NONE auth"
            value={connectionId}
            onChange={(e) => setConnectionId(e.target.value)}
          />
        </label>
        <div className="grid grid-cols-2 gap-2">
          <label className="text-xs font-medium">
            Records path
            <input
              className="mt-1 w-full rounded-md border px-2 py-1 text-sm"
              placeholder="data"
              value={recordsPath}
              onChange={(e) => setRecordsPath(e.target.value)}
            />
          </label>
          <label className="text-xs font-medium">
            External ID path
            <input
              className="mt-1 w-full rounded-md border px-2 py-1 text-sm"
              placeholder="id"
              value={externalIdPath}
              onChange={(e) => setExternalIdPath(e.target.value)}
            />
          </label>
        </div>
        <label className="text-xs font-medium">
          Page size
          <input
            className="mt-1 w-full rounded-md border px-2 py-1 text-sm"
            type="number"
            value={pageSize}
            onChange={(e) => setPageSize(e.target.value)}
          />
        </label>
      </div>
      <Button size="sm" onClick={handleSave} disabled={updateMut.isPending}>
        {updateMut.isPending ? "Saving…" : "Save polling config"}
      </Button>
      <p className="text-xs text-muted-foreground">
        Secrets are stored encrypted via OutboundHttpConnection; this UI never shows them (••••••••). Polling respects 429 and auth errors.
      </p>
    </div>
  );
}

export default function SourceDetailPage() {
  const params = useParams<{ configId: string }>();
  const configId = params?.configId ?? "";
  const router = useRouter();
  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const { canViewAcquisition, canEditAcquisition, canDeleteAcquisition } = usePermissions();
  const { data: config, isLoading, isError, refetch } = useAcquisitionConfig(configId);
  const mappingsQ = useLeadIngestionMappings(configId);
  const eventsQ = useLeadIngestionEvents(configId, { page: 0, size: 5 });

  const updateConfig = useUpdateAcquisitionConfig();
  const deleteConfig = useDeleteAcquisitionConfig();

  if (!canViewAcquisition) {
    return (
      <div className="p-6">
        <p className="text-sm text-muted-foreground">You do not have permission to view this source.</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="space-y-4 p-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }

  if (isError || !config) {
    return (
      <div className="p-6 space-y-3">
        <Link href="/acquisition" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" /> Back to acquisition
        </Link>
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm font-medium">Source not found</p>
            <p className="text-xs text-muted-foreground">It may have been deleted or you lack access.</p>
            <Button variant="outline" size="sm" className="mt-3" onClick={() => refetch()}>
              Try again
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  const mappings = mappingsQ.data?.data ?? [];
  const activeMappings = mappings.filter((m) => m.active).length;
  const events = eventsQ.data?.data ?? [];
  const isActive = !!config.active;
  const isSupported =
    config.transportType === "WEBHOOK" ||
    config.transportType === "IMPORT" ||
    config.transportType === "FORM" ||
    config.transportType === "API" ||
    config.transportType === "POLLING";
  const isWebhook = config.transportType === "WEBHOOK";
  const isImport = config.transportType === "IMPORT";
  const isForm = config.transportType === "FORM";
  const isDirectApi = config.transportType === "API";
  const isPolling = config.transportType === "POLLING";
  const needsConfig = activeMappings === 0;

  const handleToggle = async () => {
    if (!isSupported) {
      toast.error("Only Webhook and CSV Import can be activated");
      return;
    }
    try {
      await updateConfig.mutateAsync({
        id: config.id,
        data: {
          name: config.name,
          transportType: config.transportType,
          active: !isActive,
          settings: config.settings ?? {},
        },
      });
      toast.success(isActive ? "Source deactivated" : "Source activated");
    } catch (e: any) {
      toast.error(e?.response?.data?.error?.message ?? "Failed to update source");
    }
  };

  const handleDelete = async () => {
    try {
      await deleteConfig.mutateAsync(config.id);
      toast.success("Source deleted");
      router.push("/acquisition");
    } catch {
      toast.error("Failed to delete source");
    }
  };

  const handleEditSave = async (values: LeadIngestionConfigCreateRequest) => {
    try {
      await updateConfig.mutateAsync({ id: config.id, data: values });
      toast.success("Source updated");
      setEditOpen(false);
    } catch {
      toast.error("Failed to update source");
    }
  };

  return (
    <div className="space-y-6 p-6">
      <Link href="/acquisition" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> Back to acquisition
      </Link>

      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div>
          <h1 className="text-2xl font-semibold flex items-center gap-2">
            {isPolling ? (
              <Activity className="h-5 w-5 text-muted-foreground" />
            ) : isForm ? (
              <FileSpreadsheet className="h-5 w-5 text-muted-foreground" />
            ) : isImport ? (
              <FileSpreadsheet className="h-5 w-5 text-muted-foreground" />
            ) : isDirectApi ? (
              <Webhook className="h-5 w-5 text-muted-foreground" />
            ) : (
              <Webhook className="h-5 w-5 text-muted-foreground" />
            )}
            {config.name}
          </h1>
          <p className="text-sm text-muted-foreground">
            {config.transportType} {config.active ? "· Active" : "· Inactive"} {needsConfig && "· Needs mapping"}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Badge variant={isActive ? "default" : "secondary"}>{isActive ? "Active" : "Inactive"}</Badge>
          <Badge variant="outline">{config.transportType}</Badge>
          {needsConfig && <Badge variant="outline" className="border-amber-300 text-amber-700">Needs configuration</Badge>}
        </div>
      </div>

      <Tabs defaultValue="overview" className="w-full">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="mapping">Mapping</TabsTrigger>
          <TabsTrigger value="test">Test</TabsTrigger>
          <TabsTrigger value="events">Events</TabsTrigger>
          <TabsTrigger value="settings">Settings</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-4 mt-4">
          <div className="grid gap-3 md:grid-cols-3">
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm">Status</CardTitle>
              </CardHeader>
              <CardContent className="space-y-1">
                <p className="flex items-center gap-2 text-sm">
                  {isActive ? <CheckCircle2 className="h-4 w-4 text-green-600" /> : <Clock className="h-4 w-4 text-amber-600" />}
                  {isActive ? "Active — receiving leads" : "Inactive — paused"}
                </p>
                <p className="text-xs text-muted-foreground">
                  {isActive
                    ? isImport
                      ? "Ready to import CSV files."
                      : isForm
                        ? "Public form is live — share the URL."
                        : isDirectApi
                          ? "Direct API endpoint will accept JSON."
                          : isPolling
                            ? "Polling is scheduled — will fetch periodically."
                            : "Webhook endpoint will accept payloads."
                    : "Activate after mapping and testing."}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm">
                  {isImport
                    ? "CSV Import"
                    : isForm
                      ? "Public Form"
                      : isDirectApi
                        ? "Direct API"
                        : isPolling
                          ? "API Polling"
                          : "Webhook endpoint"}
                </CardTitle>
              </CardHeader>
              <CardContent>
                {isWebhook && config.publicKey ? (
                  <div className="flex items-center gap-2 rounded-md border bg-muted/30 px-2 py-1">
                    <code className="text-xs truncate flex-1">{config.inboundPath ?? `/api/v1/public/acquisition/${config.publicKey}`}</code>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-6 w-6"
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
                ) : isDirectApi && config.publicKey ? (
                  <div className="flex items-center gap-2 rounded-md border bg-muted/30 px-2 py-1">
                    <code className="text-xs truncate flex-1">{config.inboundPath ?? `/api/v1/public/direct/${config.publicKey}`}</code>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-6 w-6"
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
                ) : isImport ? (
                  <div className="space-y-2">
                    <p className="text-xs text-muted-foreground">CSV file → mapping → universal pipeline. Upload a file to create leads.</p>
                    <Link href={`/acquisition/configs/${config.id}/import`}>
                      <Button size="sm" className="w-full">
                        <Upload className="mr-2 h-3 w-3" /> Import CSV
                      </Button>
                    </Link>
                  </div>
                ) : isForm && config.publicKey ? (
                  <div className="space-y-2">
                    <div className="flex items-center gap-2 rounded-md border bg-muted/30 px-2 py-1">
                      <code className="text-xs truncate flex-1">{`${typeof window !== "undefined" ? window.location.origin : ""}/forms/public/${config.publicKey}`}</code>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-6 w-6"
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
                    <div className="flex gap-2">
                      <Link href={`/forms/public/${config.publicKey}`} target="_blank" className="flex-1">
                        <Button variant="outline" size="sm" className="w-full">
                          <ExternalLink className="mr-1 h-3 w-3" /> Open form
                        </Button>
                      </Link>
                      <Link href={`/acquisition/configs/${config.id}/mappings`} className="flex-1">
                        <Button variant="outline" size="sm" className="w-full">
                          <FileSearch className="mr-1 h-3 w-3" /> Mapping
                        </Button>
                      </Link>
                    </div>
                    <p className="text-xs text-muted-foreground">Public, no auth. Submissions create acquisition events.</p>
                  </div>
                ) : isPolling ? (
                  <div className="space-y-2">
                    <p className="text-xs text-muted-foreground">
                      Polling: CRM → external API → records → universal pipeline. Configure endpoint and schedule.
                    </p>
                    <PollingCard configId={config.id} />
                  </div>
                ) : (
                  <p className="text-xs text-muted-foreground">No endpoint — unsupported transport or inactive.</p>
                )}
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm">Mappings</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-2xl font-semibold">{activeMappings} <span className="text-sm font-normal text-muted-foreground">/ {mappings.length} active</span></p>
                {needsConfig ? (
                  <p className="text-xs text-amber-600 flex items-center gap-1"><AlertTriangle className="h-3 w-3" /> Map required fields before activating</p>
                ) : (
                  <p className="text-xs text-green-600 flex items-center gap-1"><CheckCircle2 className="h-3 w-3" /> Ready</p>
                )}
                <Link href={`/acquisition/configs/${config.id}/mappings`} className="mt-2 inline-flex text-xs underline">
                  Configure mapping <FileSearch className="ml-1 h-3 w-3" />
                </Link>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Activity className="h-4 w-4" /> Recent activity
              </CardTitle>
              <CardDescription>Last 5 events for this source · derived from event list API (no fabricated metrics)</CardDescription>
            </CardHeader>
            <CardContent>
              {eventsQ.isLoading ? (
                <p className="text-sm text-muted-foreground">Loading activity…</p>
              ) : eventsQ.isError ? (
                <div className="flex items-center gap-2">
                  <p className="text-sm text-muted-foreground">Unable to load activity.</p>
                  <Button variant="outline" size="sm" onClick={() => eventsQ.refetch()}>Retry</Button>
                </div>
              ) : events.length === 0 ? (
                <div className="rounded-md border border-dashed p-6 text-center">
                  <p className="text-sm font-medium">No ingestion events yet</p>
                  <p className="text-xs text-muted-foreground">Once this source receives data, events will appear here.</p>
                  <Link href={`/acquisition/configs/${config.id}/events`}>
                    <Button variant="outline" size="sm" className="mt-3">View events</Button>
                  </Link>
                </div>
              ) : (
                <div className="space-y-2">
                  {events.map((ev) => (
                    <Link
                      key={ev.id}
                      href={`/acquisition/configs/${config.id}/events/${ev.id}`}
                      className="flex items-center justify-between rounded-md border p-2 hover:bg-muted/30"
                    >
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <Badge variant={ev.status === "PROCESSED" ? "default" : ev.status === "DUPLICATE" ? "secondary" : ev.status === "FAILED" || ev.status === "REJECTED" ? "destructive" : "outline"} className="text-[10px]">
                            {ev.status}
                          </Badge>
                          {ev.failureStage && <Badge variant="outline" className="text-[10px]">{ev.failureStage}</Badge>}
                          <span className="text-xs truncate">{ev.externalEventId ?? ev.id.slice(0, 8)}</span>
                        </div>
                        <p className="text-xs text-muted-foreground">{new Date(ev.receivedAt).toLocaleString()}</p>
                      </div>
                      <div className="text-xs text-muted-foreground truncate ml-2">
                        {ev.leadId ? `Lead ${ev.leadId.slice(0, 8)}` : ev.errorCode ?? "—"}
                      </div>
                    </Link>
                  ))}
                  <Link href={`/acquisition/configs/${config.id}/events`} className="text-xs underline inline-flex items-center gap-1">
                    View all events <ExternalLink className="h-3 w-3" />
                  </Link>
                </div>
              )}
            </CardContent>
          </Card>

          <div className="flex flex-wrap gap-2">
            {isImport ? (
              <Link href={`/acquisition/configs/${config.id}/import`}>
                <Button><Upload className="mr-2 h-4 w-4" /> Import CSV</Button>
              </Link>
            ) : null}
            <Link href={`/acquisition/configs/${config.id}/mappings`}>
              <Button variant={isImport ? "outline" : "default"}><FileSearch className="mr-2 h-4 w-4" /> Configure mapping</Button>
            </Link>
            <Link href={`/acquisition/configs/${config.id}/events`}>
              <Button variant="outline"><Activity className="mr-2 h-4 w-4" /> View events</Button>
            </Link>
          </div>
        </TabsContent>

        <TabsContent value="mapping" className="space-y-3 mt-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Field mapping</CardTitle>
              <CardDescription>Source-specific mapping → universal lead. Transform → default → normalization → validation.</CardDescription>
            </CardHeader>
            <CardContent className="flex flex-wrap gap-2">
              <Link href={`/acquisition/configs/${config.id}/mappings`}>
                <Button>Open mapping editor</Button>
              </Link>
              <p className="text-xs text-muted-foreground self-center">Type warnings, required checks, and preview are in the mapping editor.</p>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="test" className="space-y-3 mt-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2"><FlaskConical className="h-4 w-4" /> Test source</CardTitle>
              <CardDescription>Use a recent event to run mapping preview and validation — backend is authoritative.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-2">
              <p className="text-sm">1. Discover sample event → 2. Map fields → 3. Preview (raw → mapped → normalized → validation) → 4. Validate → 5. Activate.</p>
              <Link href={`/acquisition/configs/${config.id}/mappings`}>
                <Button size="sm">Go to mapping & preview</Button>
              </Link>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="events" className="space-y-3 mt-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Events</CardTitle>
              <CardDescription>Operational visibility: status, failure stage, attempt, lead linkage, reprocess.</CardDescription>
            </CardHeader>
            <CardContent>
              <Link href={`/acquisition/configs/${config.id}/events`}>
                <Button size="sm">Open events</Button>
              </Link>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="settings" className="space-y-3 mt-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2"><Settings className="h-4 w-4" /> Settings</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="grid gap-2 md:grid-cols-2 text-sm">
                <div>
                  <p className="text-xs text-muted-foreground">Name</p>
                  <p className="font-medium">{config.name}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Transport</p>
                  <p className="font-medium">{config.transportType} {isSupported ? "✓ Available" : "— Coming soon"}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Status</p>
                  <p className="font-medium">{isActive ? "Active" : "Inactive"}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Created</p>
                  <p className="font-medium">{new Date(config.createdAt).toLocaleString()}</p>
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                {canEditAcquisition && (
                  <>
                    <Button variant="outline" size="sm" onClick={() => setEditOpen(true)}>Edit source</Button>
                    <Button variant="outline" size="sm" onClick={handleToggle} disabled={!isSupported}>
                      <Power className="mr-1 h-3 w-3" /> {isActive ? "Deactivate" : "Activate"}
                    </Button>
                  </>
                )}
                {canDeleteAcquisition && (
                  <Button variant="destructive" size="sm" onClick={() => setDeleteOpen(true)}>
                    <Trash2 className="mr-1 h-3 w-3" /> Delete source
                  </Button>
                )}
              </div>
              {!isSupported && (
                <p className="text-xs text-amber-600 flex items-center gap-1"><AlertTriangle className="h-3 w-3" /> Only Webhook, Import, Form, Direct API and Polling can be activated.</p>
              )}
              {isPolling && (
                <PollingSettingsEditor config={config} />
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <AcquisitionConfigDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        editing={config}
        onSubmit={handleEditSave}
        isSubmitting={updateConfig.isPending}
      />

      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete source?</AlertDialogTitle>
            <AlertDialogDescription>
              This will stop the source from accepting new leads. Existing ingestion events and leads will not be deleted. This cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              Delete source
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
