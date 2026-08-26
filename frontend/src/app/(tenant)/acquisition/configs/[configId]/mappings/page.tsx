"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Eye, Pencil, Plus, RefreshCw, Trash2 } from "lucide-react";
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
import { acquisitionApi } from "@/lib/api/acquisition";
import {
  useAcquisitionConfig,
  useCreateLeadIngestionMapping,
  useDeleteLeadIngestionMapping,
  useLeadIngestionMappings,
  useLeadIngestionSourceFields,
  useLeadIngestionTargetFields,
  useUpdateLeadIngestionMapping,
} from "@/lib/hooks/acquisition";
import { LeadIngestionMappingDialog } from "@/components/acquisition/LeadIngestionMappingDialog";
import { usePermissions } from "@/lib/hooks/usePermissions";
import {
  LeadIngestionFieldMappingResponse,
  LeadIngestionFieldMappingRequest,
} from "@/types/acquisition";

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
    } catch {
      toast.error("Failed to save mapping");
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
          <Link
            href="/acquisition"
            className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to configurations
          </Link>
          <h1 className="text-2xl font-semibold">
            {config ? config.name : "Field Mappings"}
          </h1>
          <p className="text-sm text-muted-foreground">
            Map incoming source fields to CRM fields. Source paths refer to the
            inbound payload — not CRM records.
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

      <Card>
        <CardHeader>
          <CardTitle>Source field discovery</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-sm text-muted-foreground">
            Source fields are discovered from a captured ingestion event. Paste an
            ingestion event ID and load its fields; without a discoverable event,
            paths can still be entered manually.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Input
              value={sourceEventIdInput}
              onChange={(e) => setSourceEventIdInput(e.target.value)}
              placeholder="Ingestion event ID (UUID)"
            />
            <Button
              variant="outline"
              onClick={() => setActiveSourceEventId(sourceEventIdInput.trim())}
              disabled={!sourceEventIdInput.trim()}
            >
              Load fields
            </Button>
          </div>
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
                <div className="flex flex-wrap gap-2">
                  {sourceFields.map((field) => (
                    <Badge key={field.path} variant="outline">
                      {field.path}
                    </Badge>
                  ))}
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
              {mappings.map((mapping) => (
                <div
                  key={mapping.id}
                  className="flex flex-col gap-3 rounded-lg border p-4 md:flex-row md:items-center md:justify-between"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-medium">{mapping.sourcePath}</span>
                      <span className="text-muted-foreground">→</span>
                      <span className="font-medium">{mapping.targetField}</span>
                      <Badge variant={mapping.active ? "default" : "secondary"}>
                        {mapping.active ? "Active" : "Inactive"}
                      </Badge>
                      {mapping.transformType !== "NONE" && (
                        <Badge variant="outline">{mapping.transformType}</Badge>
                      )}
                      {mapping.required && <Badge variant="outline">Required</Badge>}
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">{mapping.targetType}</p>
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
              ))}
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
        <div className="space-y-1 text-sm">
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
      )}
    </div>
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
