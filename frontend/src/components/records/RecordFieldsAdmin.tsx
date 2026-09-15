"use client";

import { useState } from "react";
import { Plus, Pencil, Trash2, Loader2, GripVertical, Database } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { useRecordFields, useCreateRecordField, useUpdateRecordField, useDeleteRecordField } from "@/lib/hooks/records";
import { RecordFieldResponse, RECORD_FIELD_TYPE_LABELS } from "@/types/records";
import { RecordFieldDialog } from "./RecordFieldDialog";

interface Props {
  recordTypeId: string;
}

export function RecordFieldsAdmin({ recordTypeId }: Props) {
  const { data: fields, isLoading, isError, error } = useRecordFields(recordTypeId);
  const createMutation = useCreateRecordField();
  const updateMutation = useUpdateRecordField();
  const deleteMutation = useDeleteRecordField();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<RecordFieldResponse | null>(null);
  const [toDelete, setToDelete] = useState<RecordFieldResponse | null>(null);

  const sorted = [...(fields || [])].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0) || a.fieldKey.localeCompare(b.fieldKey));
  const nextOrder = sorted.length ? Math.max(...sorted.map((f) => f.displayOrder ?? 0)) + 1 : 0;
  const existingKeys = sorted.map((f) => f.fieldKey);

  function openCreate() {
    setEditing(null);
    setDialogOpen(true);
  }
  function openEdit(f: RecordFieldResponse) {
    setEditing(f);
    setDialogOpen(true);
  }
  function handleSubmit(data: any) {
    if (editing) {
      updateMutation.mutate(
        { recordTypeId, fieldId: editing.id, data: { fieldLabel: data.fieldLabel, fieldType: data.fieldType, isRequired: data.isRequired, isActive: data.isActive, displayOrder: data.displayOrder, options: data.options, defaultValue: data.defaultValue } },
        { onSuccess: () => setDialogOpen(false) }
      );
    } else {
      createMutation.mutate({ recordTypeId, data }, { onSuccess: () => setDialogOpen(false) });
    }
  }

  const isPending = createMutation.isPending || updateMutation.isPending;

  if (isLoading) {
    return (
      <div className="flex justify-center py-12">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }
  if (isError) {
    return (
      <div className="rounded-lg border border-destructive/20 bg-destructive/5 p-4 text-sm text-destructive">
        Failed to load fields. {(error as any)?.response?.data?.error?.message || (error as Error).message}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="font-semibold">Fields</h3>
          <p className="text-sm text-muted-foreground">Define what data this Record Type stores. Field keys are the stable identifiers for APIs and workflows.</p>
        </div>
        <Button onClick={openCreate} size="sm">
          <Plus className="mr-2 h-4 w-4" /> Add Field
        </Button>
      </div>

      <div className="rounded-xl border bg-white overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40">
              <TableHead className="w-6"></TableHead>
              <TableHead>Label</TableHead>
              <TableHead>Key</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Required</TableHead>
              <TableHead>Active</TableHead>
              <TableHead>Order</TableHead>
              <TableHead className="w-[100px]"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {sorted.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} className="h-48">
                  <div className="flex flex-col items-center justify-center gap-3 py-6 text-center">
                    <div className="rounded-full bg-muted p-4">
                      <Database className="h-6 w-6 text-muted-foreground" />
                    </div>
                    <div>
                      <p className="font-medium">No fields yet</p>
                      <p className="text-sm text-muted-foreground">Add fields to define what data this Record Type stores.</p>
                    </div>
                    <Button onClick={openCreate} size="sm" variant="outline">
                      <Plus className="mr-2 h-4 w-4" /> Add Field
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              sorted.map((f) => (
                <TableRow key={f.id} className={!f.isActive ? "opacity-60 bg-muted/20" : ""}>
                  <TableCell>
                    <GripVertical className="h-4 w-4 text-muted-foreground/40" />
                  </TableCell>
                  <TableCell>
                    <div className="font-medium">{f.fieldLabel}</div>
                    {f.defaultValue && <div className="text-xs text-muted-foreground">Default: {f.defaultValue}</div>}
                  </TableCell>
                  <TableCell className="font-mono text-xs">{f.fieldKey}</TableCell>
                  <TableCell>
                    <Badge variant="outline" className="font-normal">
                      {RECORD_FIELD_TYPE_LABELS[f.fieldType]} <span className="ml-1 text-xs text-muted-foreground">{f.fieldType}</span>
                    </Badge>
                    {f.fieldType === "ENUM" && f.options && f.options.length > 0 && (
                      <div className="mt-1 flex flex-wrap gap-1 max-w-[220px]">
                        {f.options.map((o) => (
                          <Badge key={o} variant="secondary" className="text-[11px] px-1 py-0">
                            {o}
                          </Badge>
                        ))}
                      </div>
                    )}
                  </TableCell>
                  <TableCell>{f.isRequired ? <Badge className="bg-amber-50 text-amber-700 border-amber-200">Required</Badge> : <span className="text-muted-foreground text-xs">—</span>}</TableCell>
                  <TableCell>{f.isActive ? <Badge className="bg-emerald-50 text-emerald-700 border-emerald-200">Active</Badge> : <Badge variant="outline" className="text-muted-foreground">Inactive</Badge>}</TableCell>
                  <TableCell className="text-sm">{f.displayOrder}</TableCell>
                  <TableCell>
                    <div className="flex gap-1">
                      <Button variant="ghost" size="icon" onClick={() => openEdit(f)} aria-label="Edit field">
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon" className="text-destructive hover:text-destructive" onClick={() => setToDelete(f)} aria-label="Delete field">
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      <RecordFieldDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        editing={editing}
        existingKeys={existingKeys.filter((k) => k !== editing?.fieldKey)}
        nextOrder={nextOrder}
        onSubmit={handleSubmit}
        isPending={isPending}
      />

      <AlertDialog open={!!toDelete} onOpenChange={() => setToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove field?</AlertDialogTitle>
            <AlertDialogDescription>
              This will deactivate <strong>{toDelete?.fieldLabel}</strong> (<span className="font-mono">{toDelete?.fieldKey}</span>). Existing records will keep their historical values, but new records will no longer accept this field. This is a soft-delete.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive hover:bg-destructive/90"
              onClick={() => toDelete && deleteMutation.mutate({ recordTypeId, fieldId: toDelete.id }, { onSuccess: () => setToDelete(null) })}
            >
              Remove
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
