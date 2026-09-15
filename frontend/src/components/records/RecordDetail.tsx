"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Loader2, Pencil, Trash2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
import { useRecord, useRecordFields, useDisplayConfig, useDeleteRecord, useUpdateRecord } from "@/lib/hooks/records";
import { RecordForm } from "./RecordForm";
import { FieldValueRenderer } from "./FieldValueRenderer";
import { RelatedRecordsPanel } from "./RelatedRecordsPanel";
import { RecordFieldResponse } from "@/types/records";

interface Props {
  recordId: string;
}

export function RecordDetail({ recordId }: Props) {
  const { data: record, isLoading, isError, error } = useRecord(recordId);
  const recordTypeId = record?.recordTypeId;
  const { data: fields } = useRecordFields(recordTypeId);
  const { data: display } = useDisplayConfig(recordTypeId);
  const deleteMutation = useDeleteRecord();
  const updateMutation = useUpdateRecord();
  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  if (isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }
  if (isError || !record) {
    return <div className="rounded-lg border border-destructive/20 bg-destructive/5 p-6 text-center text-sm text-destructive">Failed to load record: {(error as any)?.response?.data?.error?.message || "Not found"}</div>;
  }

  const fieldById = new Map<string, RecordFieldResponse>((fields || []).map((f) => [f.id, f]));
  const fieldByKey = new Map<string, RecordFieldResponse>((fields || []).map((f) => [f.fieldKey, f]));

  // Derive sections from display or default
  let sections: { id: string; name: string; fieldIds: string[] }[];
  if (display?.detail?.sections && display.detail.sections.length > 0) {
    sections = display.detail.sections.map((s) => ({ id: s.id, name: s.name, fieldIds: s.fieldIds || [] }));
  } else if (fields) {
    const active = [...fields].filter((f) => f.isActive).sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0));
    sections = [{ id: "default", name: "Details", fieldIds: active.map((f) => f.id) }];
  } else {
    sections = [];
  }

  // Filter out deleted/inactive refs safely – show placeholder
  const hasInactive = sections.some((s) => s.fieldIds.some((fid) => {
    const f = fieldById.get(fid);
    return f && !f.isActive;
  }));

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between rounded-xl border bg-white p-4 shadow-sm">
        <div>
          <h2 className="font-semibold flex items-center gap-2">
            {record.recordTypeName} <Badge variant="outline" className="font-mono text-xs">{record.recordTypeKey}</Badge>
          </h2>
          <p className="text-xs text-muted-foreground font-mono">{record.id}</p>
          <p className="text-xs text-muted-foreground">Created {new Date(record.createdAt).toLocaleString()} · Updated {new Date(record.updatedAt).toLocaleString()}</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => setEditOpen(true)}>
            <Pencil className="h-4 w-4 mr-1" /> Edit
          </Button>
          <Button variant="destructive" size="sm" onClick={() => setDeleteOpen(true)}>
            <Trash2 className="h-4 w-4 mr-1" /> Archive
          </Button>
        </div>
      </div>

      {hasInactive && <div className="rounded-lg bg-amber-50 border border-amber-200 p-2 text-xs text-amber-800">Some fields in this layout are inactive – historical values are preserved but new edits will hide them.</div>}

      <div className="space-y-4">
        {sections.map((sec) => (
          <Card key={sec.id} className="shadow-sm">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-semibold text-indigo-700">{sec.name}</CardTitle>
            </CardHeader>
            <CardContent>
              {sec.fieldIds.length === 0 ? (
                <p className="text-sm text-muted-foreground">No fields in this section.</p>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {sec.fieldIds.map((fid) => {
                    const f = fieldById.get(fid);
                    if (!f) {
                      return (
                        <div key={fid} className="rounded border border-dashed p-2 text-xs text-muted-foreground">
                          Unknown field {fid.slice(0, 8)} – deleted
                        </div>
                      );
                    }
                    const value = record.data?.[f.fieldKey];
                    // For inactive fields historical value still shown
                    return (
                      <div key={fid} className="space-y-1 rounded-lg border bg-muted/20 p-3">
                        <p className="text-xs font-medium text-muted-foreground">
                          {f.fieldLabel} {f.isRequired && <span className="text-destructive">*</span>} {!f.isActive && <Badge variant="outline" className="ml-1 border-amber-200 text-amber-700 text-[10px]">Inactive</Badge>}
                          {f.fieldType === "REFERENCE" && f.referenceEntityType && <Badge variant="outline" className="ml-1 text-[10px]">{f.referenceEntityType}</Badge>}
                        </p>
                        <div className="text-sm">
                          <FieldValueRenderer field={f} value={value} variant="detail" />
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      {fields && <RelatedRecordsPanel fields={fields} data={record.data as Record<string, unknown>} />}

      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Edit Record</DialogTitle>
          </DialogHeader>
          {fields && (
            <RecordForm
              fields={fields}
              initialData={record.data as Record<string, unknown>}
              isPending={updateMutation.isPending}
              submitLabel="Save Changes"
              onSubmit={(data) =>
                updateMutation.mutate(
                  { id: record.id, data: { data } },
                  { onSuccess: () => setEditOpen(false) }
                )
              }
            />
          )}
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Archive record?</AlertDialogTitle>
            <AlertDialogDescription>This record will be archived and removed from normal lists. Historical data is preserved (soft-delete).</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive hover:bg-destructive/90"
              onClick={() => deleteMutation.mutate(record.id, { onSuccess: () => (window.location.href = "/records?recordTypeId=" + record.recordTypeId) })}
            >
              Archive
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
