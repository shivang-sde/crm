"use client";

import { useState, useMemo } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeft,
  Eye,
  Pencil,
  Plus,
  RefreshCw,
  Trash2,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Copy,
  Circle,
} from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { acquisitionApi } from "@/lib/api/acquisition";
import {
  useAcquisitionConfig,
  useCreateLeadIngestionMapping,
  useDeleteLeadIngestionMapping,
  useLeadIngestionMappings,
  useLeadIngestionSourceFields,
  useLeadIngestionTargetFields,
  useUpdateLeadIngestionMapping,
  useLeadIngestionEvents,
} from "@/lib/hooks/acquisition";
import { LeadIngestionMappingDialog } from "@/components/acquisition/LeadIngestionMappingDialog";
import { usePermissions } from "@/lib/hooks/usePermissions";
import {
  LeadIngestionFieldMappingResponse,
  LeadIngestionFieldMappingRequest,
  LeadIngestionTargetField,
  LeadIngestionSourceField,
} from "@/types/acquisition";

const friendlySourceLabel = (path: string): string => {
  const last = path.split(".").pop() ?? path;
  return last
    .replace(/[_-]+/g, " ")
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .split(" ")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(" ");
};

function getCompatibility(
  sourceType: string | null | undefined,
  targetDataType: string | null | undefined
): "compatible" | "warning" | "incompatible" {
  const src = (sourceType ?? "").toUpperCase();
  const tgt = (targetDataType ?? "STRING").toUpperCase();
  if (!src || src === "NULL") return "compatible";
  if (src === "OBJECT") return "incompatible";
  if (src === "ARRAY") {
    return tgt === "MULTISELECT" ? "compatible" : "incompatible";
  }
  if (src === "NUMBER") {
    if (tgt === "NUMBER") return "compatible";
    if (["TEXT", "TEXTAREA", "STRING"].includes(tgt)) return "warning";
    return "warning";
  }
  if (src === "BOOLEAN") {
    if (tgt === "BOOLEAN") return "compatible";
    if (["TEXT", "TEXTAREA", "STRING"].includes(tgt)) return "warning";
    return "warning";
  }
  // STRING source
  if (["NUMBER", "DATE", "BOOLEAN"].includes(tgt)) return "warning";
  if (
    ["STRING", "TEXT", "TEXTAREA", "EMAIL", "PHONE", "URL", "SELECT", "MULTISELECT"].includes(tgt)
  ) {
    return "compatible";
  }
  return "warning";
}

export default function AcquisitionMappingsPage() {
  const params = useParams<{ configId: string }>();
  const configId = params?.configId ?? "";

  const { canViewAcquisition, canEditAcquisition, canDeleteAcquisition } =
    usePermissions();

  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<LeadIngestionFieldMappingResponse | null>(null);
  const [sourceEventIdInput, setSourceEventIdInput] = useState("");
  const [activeSourceEventId, setActiveSourceEventId] = useState("");
  const [previewOpen, setPreviewOpen] = useState(false);

  const { data: configData } = useAcquisitionConfig(configId);
  const mappingsQuery = useLeadIngestionMappings(configId);
  const targetFieldsQuery = useLeadIngestionTargetFields(configId);
  const sourceFieldsQuery = useLeadIngestionSourceFields(
    configId,
    activeSourceEventId || undefined
  );
  const recentEventsQuery = useLeadIngestionEvents(configId, { page: 0, size: 5 });

  const createMapping = useCreateLeadIngestionMapping(configId);
  const updateMapping = useUpdateLeadIngestionMapping(configId, editing?.id ?? "");
  const deleteMapping = useDeleteLeadIngestionMapping(configId);

  if (!canViewAcquisition) {
    return (
      <div className="space-y-6 p-6">
        <h1 className="text-2xl font-semibold">Field Mappings</h1>
        <p className="text-sm text-muted-foreground">
          You do not have permission to view acquisition mappings.
        </p>
      </div>
    );
  }

  const config = configData;
  const mappings = mappingsQuery.data?.data ?? [];
  const sourceFields = sourceFieldsQuery.data?.data ?? [];
  const targetFields = targetFieldsQuery.data?.data ?? [];
  const recentEvents = recentEventsQuery.data?.data ?? [];

  // Stats
  const mappedSourcePaths = useMemo(
    () => new Set(mappings.filter((m) => m.active).map((m) => m.sourcePath)),
    [mappings]
  );
  const sourceFieldStats = useMemo(() => {
    const total = sourceFields.length;
    const mapped = sourceFields.filter((f) => mappedSourcePaths.has(f.path)).length;
    return { total, mapped, unmapped: total - mapped };
  }, [sourceFields, mappedSourcePaths]);

  const targetByKey = useMemo(() => {
    const m = new Map<string, LeadIngestionTargetField>();
    for (const tf of targetFields) m.set(`${tf.targetType}:${tf.fieldKey}`, tf);
    return m;
  }, [targetFields]);

  const sourceByPath = useMemo(() => {
    const m = new Map<string, LeadIngestionSourceField>();
    for (const sf of sourceFields) m.set(sf.path, sf);
    return m;
  }, [sourceFields]);

  const requiredMissing = useMemo(() => {
    return targetFields.filter(
      (tf) =>
        tf.required &&
        !mappings.some(
          (mm) => mm.active && mm.targetType === tf.targetType && mm.targetField === tf.fieldKey
        )
    );
  }, [targetFields, mappings]);

  const mappingWarnings = useMemo(() => {
    const warnings: { mapping: LeadIngestionFieldMappingResponse; level: string; reason: string }[] = [];
    const seen = new Map<string, number>();
    for (const m of mappings) {
      if (!m.active) continue;
      const key = `${m.targetType}:${m.targetField}`;
      seen.set(key, (seen.get(key) ?? 0) + 1);
    }
    for (const m of mappings) {
      if (!m.active) continue;
      const tf = targetByKey.get(`${m.targetType}:${m.targetField}`);
      const sf = sourceByPath.get(m.sourcePath);
      const type = sf?.detectedType ?? null;
      const compat = getCompatibility(type, tf?.dataType);
      if (compat === "warning") {
        warnings.push({
          mapping: m,
          level: "warning",
          reason: `Source type ${type ?? "unknown"} → ${tf?.dataType ?? "STRING"} may need conversion`,
        });
      } else if (compat === "incompatible") {
        warnings.push({
          mapping: m,
          level: "incompatible",
          reason: `Source type ${type ?? "unknown"} is incompatible with ${tf?.dataType ?? "STRING"} (e.g. array/object to scalar)`,
        });
      }
      if ((seen.get(`${m.targetType}:${m.targetField}`) ?? 0) > 1) {
        warnings.push({
          mapping: m,
          level: "incompatible",
          reason: `Duplicate target: multiple sources map to ${m.targetType}:${m.targetField}`,
        });
      }
    }
    return warnings;
  }, [mappings, targetByKey, sourceByPath]);

  const duplicateTargets = useMemo(() => {
    const counts = new Map<string, number>();
    for (const m of mappings.filter((x) => x.active)) {
      const k = `${m.targetType}:${m.targetField}`;
      counts.set(k, (counts.get(k) ?? 0) + 1);
    }
    return [...counts.entries()].filter(([, c]) => c > 1).map(([k]) => k);
  }, [mappings]);

  const handleSave = async (values: LeadIngestionFieldMappingRequest) => {
    try {
      if (editing?.id) {
        await updateMapping.mutateAsync(values);
        toast.success("Mapping updated successfully");
      } else {
        await createMapping.mutateAsync(values);
        toast.success("Mapping created successfully");
      }
      setOpen(false);
      setEditing(null);
    } catch (e: any) {
      const msg =
        e?.response?.data?.error?.message ??
        e?.response?.data?.message ??
        "Failed to save mapping";
      toast.error(msg);
    }
  };

  const handleToggleActive = async (mapping: LeadIngestionFieldMappingResponse) => {
    try {
      await updateMapping.mutateAsync({
        sourcePath: mapping.sourcePath,
        targetType: mapping.targetType,
        targetField: mapping.targetField,
        transformType: mapping.transformType,
        transformConfig: mapping.transformConfig ?? null,
        defaultValue: mapping.defaultValue ?? null,
        required: mapping.required ?? false,
        active: !mapping.active,
        displayOrder: mapping.displayOrder ?? 0,
      });
      toast.success(mapping.active ? "Mapping deactivated" : "Mapping activated");
    } catch {
      toast.error("Failed to update mapping");
    }
  };

  const handleDelete = async (mappingId: string) => {
    try {
      await deleteMapping.mutateAsync(mappingId);
      toast.success("Mapping deleted successfully");
    } catch {
      toast.error("Failed to delete mapping");
    }
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
              <ArrowLeft className="h-3 w-3" /> {config?.name ?? "Source"}
            </Link>
          </div>
          <h1 className="text-2xl font-semibold">
            Map Information
          </h1>
          <p className="text-sm text-muted-foreground">
            Connect information from <span className="font-medium">{config?.name ?? "your lead source"}</span> to CRM fields.
          </p>
        </div>

        {canEditAcquisition && (
          <Button
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            <Plus className="mr-2 h-4 w-4" /> Add Mapping
          </Button>
        )}
      </div>

      {/* Stats — business language */}
      <Card>
        <CardContent className="pt-6">
          <div className="grid gap-3 md:grid-cols-4">
            <div className="rounded-md border p-3">
              <p className="text-xs text-muted-foreground">Incoming information</p>
              <p className="text-lg font-semibold">
                {sourceFields.length ? `${sourceFieldStats.mapped} / ${sourceFieldStats.total}` : "—"}
                <span className="ml-2 text-xs font-normal text-muted-foreground">mapped</span>
              </p>
              <p className="text-xs text-muted-foreground">
                {sourceFieldStats.unmapped} not yet mapped
              </p>
            </div>
            <div className="rounded-md border p-3">
              <p className="text-xs text-muted-foreground">Mapped fields</p>
              <p className="text-lg font-semibold">{mappings.filter((m) => m.active).length}</p>
              <p className="text-xs text-muted-foreground">{mappings.length} total</p>
            </div>
            <div className="rounded-md border p-3">
              <p className="text-xs text-muted-foreground">Required fields</p>
              <p className={`text-lg font-semibold ${requiredMissing.length ? "text-red-600" : "text-green-600"}`}>
                {requiredMissing.length ? `${requiredMissing.length} missing` : "All set ✓"}
              </p>
              <p className="text-xs text-muted-foreground">
                {requiredMissing.length
                  ? requiredMissing.map((r) => r.label).join(", ")
                  : "All required fields are mapped"}
              </p>
            </div>
            <div className="rounded-md border p-3">
              <p className="text-xs text-muted-foreground">Needs attention</p>
              <p className={`text-lg font-semibold ${mappingWarnings.length || duplicateTargets.length ? "text-amber-600" : "text-green-600"}`}>
                {mappingWarnings.length + duplicateTargets.length ? `${mappingWarnings.length + duplicateTargets.length} items` : "None"}
              </p>
              <p className="text-xs text-muted-foreground">
                {mappingWarnings.length ? `${mappingWarnings.length} mapping warnings` : "No warnings"}
              </p>
            </div>
          </div>
          {(requiredMissing.length > 0 || duplicateTargets.length > 0) && (
            <div className="mt-3 flex flex-wrap gap-2">
              {requiredMissing.map((r) => (
                <Badge key={`${r.targetType}:${r.fieldKey}`} variant="destructive">
                  Missing: {r.label}
                </Badge>
              ))}
              {duplicateTargets.map((k) => {
                const tf = targetByKey.get(k);
                return (
                  <Badge key={k} variant="destructive">
                    Duplicate: {tf?.label ?? k}
                  </Badge>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Incoming Information</CardTitle>
          <p className="text-sm text-muted-foreground">We found these fields from your source. Map them to your CRM.</p>
        </CardHeader>
        <CardContent className="space-y-3">
          {recentEvents.length > 0 ? (
            <div className="flex flex-col gap-2 sm:flex-row">
              <Select
                value={activeSourceEventId}
                onValueChange={(v) => {
                  setActiveSourceEventId(v);
                  setSourceEventIdInput(v);
                }}
              >
                <SelectTrigger className="w-full sm:w-[360px]">
                  <SelectValue placeholder="Choose a recent lead example" />
                </SelectTrigger>
                <SelectContent>
                  {recentEvents.map((ev) => (
                    <SelectItem key={ev.id} value={ev.id}>
                      {ev.externalEventId ?? `Lead ${ev.id.slice(0, 8)}`} — {ev.status} · {new Date(ev.receivedAt).toLocaleDateString()}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button variant="outline" onClick={() => setActiveSourceEventId(sourceEventIdInput.trim())} disabled={!sourceEventIdInput.trim()}>
                Reload
              </Button>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">We haven&apos;t received sample information yet. Send a test lead to discover the fields provided by this source.</p>
          )}
          <details className="text-xs">
            <summary className="cursor-pointer text-muted-foreground">Advanced: paste event ID</summary>
            <div className="mt-2 flex gap-2">
              <Input value={sourceEventIdInput} onChange={(e) => setSourceEventIdInput(e.target.value)} placeholder="Lead example ID" />
              <Button variant="outline" onClick={() => setActiveSourceEventId(sourceEventIdInput.trim())} disabled={!sourceEventIdInput.trim()}>
                Load
              </Button>
            </div>
          </details>
          {activeSourceEventId && (
            <>
              {sourceFieldsQuery.isLoading && (
                <p className="text-sm text-muted-foreground">Loading source fields…</p>
              )}
              {!sourceFieldsQuery.isLoading && sourceFields.length === 0 && (
                <p className="text-sm text-muted-foreground">
                  No source fields discovered for this event yet.
                </p>
              )}
              {sourceFields.length > 0 && (
                <div className="space-y-2">
                  <div className="flex flex-wrap gap-2">
                    {sourceFields.map((field) => {
                      const isMapped = mappedSourcePaths.has(field.path);
                      return (
                        <div
                          key={field.path}
                          className={`flex flex-col gap-1 rounded-md border px-2 py-1 text-xs ${isMapped ? "bg-green-50 border-green-200 dark:bg-green-950/20" : "bg-muted/20"}`}
                        >
                          <div className="flex items-center gap-1">
                            {isMapped ? (
                              <CheckCircle2 className="h-3 w-3 text-green-600" />
                            ) : (
                              <Circle className="h-3 w-3 text-muted-foreground" />
                            )}
                            <span className="font-medium">{friendlySourceLabel(field.path)}</span>
                            {isMapped && <Badge className="text-[10px] px-1 py-0">✓ Mapped</Badge>}
                          </div>
                          {field.sampleValue != null && (
                            <span className="text-muted-foreground truncate max-w-[220px]">
                              e.g. {String(field.sampleValue).slice(0, 40)}
                            </span>
                          )}
                          <span className="text-[10px] text-muted-foreground">{field.path}</span>
                        </div>
                      );
                    })}
                  </div>
                  <p className="text-xs text-muted-foreground">
                    We found {sourceFields.length} fields from your source. {sourceFieldStats.mapped} mapped, {sourceFieldStats.unmapped} not yet mapped.
                  </p>
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between gap-4">
            <CardTitle>CRM Mappings</CardTitle>
            {canEditAcquisition && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPreviewOpen(true)}
              >
                <Eye className="mr-2 h-4 w-4" /> Preview
              </Button>
            )}
          </div>
        </CardHeader>
        <CardContent>
          {mappingsQuery.isLoading ? (
            <p className="text-sm text-muted-foreground">Loading mappings…</p>
          ) : mappings.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No field mappings configured yet. Map incoming source fields to CRM
              fields to control Lead creation.
            </p>
          ) : (
            <div className="space-y-3">
              {mappings.map((mapping) => {
                const tf = targetByKey.get(`${mapping.targetType}:${mapping.targetField}`);
                const sf = sourceByPath.get(mapping.sourcePath);
                const compat = getCompatibility(sf?.detectedType, tf?.dataType);
                const isDup = duplicateTargets.includes(`${mapping.targetType}:${mapping.targetField}`);
                return (
                  <div
                    key={mapping.id}
                    className={`flex flex-col gap-3 rounded-lg border p-4 md:flex-row md:items-center md:justify-between ${isDup ? "border-red-300 bg-red-50/40 dark:bg-red-950/20" : ""}`}
                  >
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-medium">{friendlySourceLabel(mapping.sourcePath)}</span>
                        <span className="text-xs text-muted-foreground">({mapping.sourcePath})</span>
                        {sf?.sampleValue != null && (
                          <span className="text-xs text-muted-foreground truncate max-w-[120px]">
                            e.g. {String(sf.sampleValue).slice(0, 30)}
                          </span>
                        )}
                        <span className="text-muted-foreground">→</span>
                        <span className="font-medium">{tf?.label ?? mapping.targetField}</span>
                        {tf?.required && <span className="text-xs text-red-600">*</span>}
                        {compat === "warning" && (
                          <Badge variant="outline" className="border-amber-300 text-amber-700">
                            <AlertTriangle className="h-3 w-3 mr-1" /> May need conversion
                          </Badge>
                        )}
                        {compat === "incompatible" && (
                          <Badge variant="destructive">
                            <XCircle className="h-3 w-3 mr-1" /> Incompatible
                          </Badge>
                        )}
                        {isDup && (
                          <Badge variant="destructive">
                            <Copy className="h-3 w-3 mr-1" /> Duplicate
                          </Badge>
                        )}
                      </div>
                      <p className="mt-1 text-xs text-muted-foreground">
                        {tf?.label ? `${tf.label} · ` : ""}
                        {mapping.required ? "Required · " : ""}
                        {isDup ? "Multiple sources → same CRM field" : compat === "warning" ? "Different types — will be kept as text" : ""}
                      </p>
                    </div>

                    {(canEditAcquisition || canDeleteAcquisition) && (
                      <div className="flex flex-wrap items-center gap-2">
                        {canEditAcquisition && (
                          <>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => {
                                setEditing(mapping);
                                setOpen(true);
                              }}
                            >
                              <Pencil className="mr-2 h-4 w-4" /> Edit
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleToggleActive(mapping)}
                            >
                              <RefreshCw className="mr-2 h-4 w-4" />
                              {mapping.active ? "Deactivate" : "Activate"}
                            </Button>
                          </>
                        )}
                        {canDeleteAcquisition && (
                          <Button
                            variant="destructive"
                            size="sm"
                            onClick={() => handleDelete(mapping.id)}
                          >
                            <Trash2 className="mr-2 h-4 w-4" /> Delete
                          </Button>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>

      <LeadIngestionMappingDialog
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (!next) setEditing(null);
        }}
        configId={configId}
        targetFields={targetFieldsQuery.data?.data ?? []}
        sourceFields={sourceFields}
        editing={editing}
        onSubmit={handleSave}
        isSubmitting={createMapping.isPending || updateMapping.isPending}
      />

      <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>Mapping preview</DialogTitle>
          </DialogHeader>
          <PreviewContent configId={configId} />
        </DialogContent>
      </Dialog>
    </div>
  );
}

function PreviewContent({ configId }: { configId: string }) {
  const [eventId, setEventId] = useState("");
  const [submittedEventId, setSubmittedEventId] = useState("");

  const enabled = Boolean(configId) && Boolean(submittedEventId);

  const previewQuery = useQuery({
    queryKey: ["acquisition", "preview", configId, submittedEventId],
    queryFn: () => acquisitionApi.previewMapping(configId, submittedEventId),
    enabled,
    retry: false,
    staleTime: 0,
  });

  const validateQuery = useQuery({
    queryKey: ["acquisition", "validatePreview", configId, submittedEventId],
    queryFn: () => acquisitionApi.validatePreview(configId, submittedEventId),
    enabled,
    retry: false,
    staleTime: 0,
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-2 sm:flex-row">
        <Input
          value={eventId}
          onChange={(e) => setEventId(e.target.value)}
          placeholder="Ingestion event ID (UUID)"
        />
        <Button
          variant="outline"
          onClick={() => setSubmittedEventId(eventId.trim())}
          disabled={!eventId.trim()}
        >
          Run preview
        </Button>
      </div>
      <p className="text-xs text-muted-foreground">
        Backend is authoritative: raw → transform (type+config) → default → normalization → validation. Preview shows actual execution.
      </p>

      {enabled && (previewQuery.isFetching || validateQuery.isFetching) && (
        <p className="text-sm text-muted-foreground">Running preview…</p>
      )}

      {enabled && !previewQuery.isFetching && previewQuery.isError && (
        <p className="text-sm text-red-500">Preview failed for this event.</p>
      )}

      {enabled && previewQuery.data && (
        <div className="space-y-1 text-sm">
          <p className="font-medium">Mapped values</p>
          <MappedSection title="Standard" values={previewQuery.data.standardFields} />
          <MappedSection title="System" values={previewQuery.data.systemFields} />
          <MappedSection title="Custom" values={previewQuery.data.customFields} />
          {(previewQuery.data.errors ?? []).map((error, index) => (
            <p key={index} className="text-red-500">{error}</p>
          ))}
        </div>
      )}

      {enabled && validateQuery.data && (
        <div className="space-y-3 text-sm">
          <div className="space-y-1">
            <p className="font-medium">Normalized values</p>
            <div className="rounded-md border bg-muted/20 p-3">
              <div className="grid gap-1 text-xs">
                <ValidatedRow label="First name" value={validateQuery.data.firstName} />
                <ValidatedRow label="Last name" value={validateQuery.data.lastName} />
                <ValidatedRow label="Email" value={validateQuery.data.email} />
                <ValidatedRow label="Phone" value={validateQuery.data.phone} />
                <ValidatedRow label="Company" value={validateQuery.data.company} />
                <ValidatedRow label="Source" value={validateQuery.data.sourceValue} />
                <ValidatedRow label="Status" value={validateQuery.data.statusValue} />
                <ValidatedRow label="Custom data" value={validateQuery.data.customData ? JSON.stringify(validateQuery.data.customData) : null} />
              </div>
            </div>
          </div>
          <div className="space-y-1">
            <p className="font-medium">Validation</p>
            {(validateQuery.data.errors ?? []).length === 0 ? (
              <Badge>Valid</Badge>
            ) : (
              <div className="space-y-1">
                <Badge variant="destructive">Validation errors</Badge>
                {(validateQuery.data.errors ?? []).map((error, index) => (
                  <p key={index} className="text-sm text-red-500">
                    {error.field ? `${error.field}: ` : ""}
                    {error.code ? `${error.code} - ` : ""}
                    {error.message}
                  </p>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function ValidatedRow({ label, value }: { label: string; value: unknown }) {
  const display = value == null || value === "" ? "—" : String(value);
  return (
    <p>
      <span className="text-muted-foreground">{label}:</span>{" "}
      <span className="font-medium break-all">{display}</span>
    </p>
  );
}

function MappedSection({
  title,
  values,
}: {
  title: string;
  values?: Record<string, unknown> | null;
}) {
  const entries = Object.entries(values ?? {});
  if (entries.length === 0) return null;

  return (
    <div>
      <p className="text-xs font-medium uppercase text-muted-foreground">{title}</p>
      <div className="space-y-0.5">
        {entries.map(([key, value]) => (
          <p key={key}>
            {key}: <span className="font-medium">{String(value)}</span>
          </p>
        ))}
      </div>
    </div>
  );
}
