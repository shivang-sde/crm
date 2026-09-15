"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Plus, Pencil, Trash2, Database, SearchX, Loader2, Settings2, Layers } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { RecordTypeDialog } from "./RecordTypeDialog";
import { useRecordTypes, useCreateRecordType, useUpdateRecordType, useDeleteRecordType, useRecordFields } from "@/lib/hooks/records";
import { RecordTypeResponse } from "@/types/records";

function FieldCountBadge({ recordTypeId }: { recordTypeId: string }) {
  const { data: fields, isLoading } = useRecordFields(recordTypeId);
  if (isLoading) return <span className="text-xs text-muted-foreground">…</span>;
  const count = fields?.length ?? 0;
  return <Badge variant="secondary">{count} field{count !== 1 ? "s" : ""}</Badge>;
}

export function RecordTypesAdmin() {
  const router = useRouter();
  const [page] = useState(0);
  const { data, isLoading, isError, error } = useRecordTypes(page, 50);
  const createMutation = useCreateRecordType();
  const updateMutation = useUpdateRecordType();
  const deleteMutation = useDeleteRecordType();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<RecordTypeResponse | null>(null);
  const [toDelete, setToDelete] = useState<RecordTypeResponse | null>(null);
  const [filter, setFilter] = useState("");

  const list = data?.data ?? [];
  const filtered = filter.trim()
    ? list.filter((r) => r.name.toLowerCase().includes(filter.toLowerCase()) || r.key.toLowerCase().includes(filter.toLowerCase()))
    : list;

  function openCreate() {
    setEditing(null);
    setDialogOpen(true);
  }
  function openEdit(r: RecordTypeResponse) {
    setEditing(r);
    setDialogOpen(true);
  }
  function handleSubmit(d: { key: string; name: string; description?: string; isActive: boolean }) {
    if (editing) {
      updateMutation.mutate(
        { id: editing.id, data: { name: d.name, description: d.description, isActive: d.isActive } },
        { onSuccess: () => setDialogOpen(false) }
      );
    } else {
      createMutation.mutate(d, { onSuccess: () => setDialogOpen(false) });
    }
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
        <p className="text-sm text-destructive">Failed to load record types.</p>
        <p className="text-xs text-muted-foreground mt-1">{(error as any)?.response?.data?.error?.message || (error as Error)?.message || "Unknown error"}</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative flex-1 max-w-sm">
          <Input placeholder="Search by name or key..." value={filter} onChange={(e) => setFilter(e.target.value)} className="pl-9" />
          <SearchX className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground opacity-50" />
        </div>
        <Button onClick={openCreate} className="w-full sm:w-auto">
          <Plus className="mr-2 h-4 w-4" /> New Record Type
        </Button>
      </div>

      <div className="rounded-xl border bg-white overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40">
              <TableHead>Name</TableHead>
              <TableHead>Key</TableHead>
              <TableHead>Fields</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="hidden md:table-cell">Updated</TableHead>
              <TableHead className="w-[200px] text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filtered.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="h-64">
                  <div className="flex flex-col items-center justify-center gap-3 py-6 text-center">
                    <div className="rounded-full bg-muted p-4">
                      <Database className="h-8 w-8 text-muted-foreground" />
                    </div>
                    <div>
                      <p className="font-medium">No Record Types yet</p>
                      <p className="text-sm text-muted-foreground max-w-sm">Create your first Record Type to start defining custom business data. Example: CDR, Payment, Shipment.</p>
                    </div>
                    <Button onClick={openCreate} size="sm">
                      <Plus className="mr-2 h-4 w-4" /> Create Record Type
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((r) => (
                <TableRow key={r.id} className="hover:bg-muted/20">
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <div className="h-8 w-8 rounded-lg bg-indigo-50 flex items-center justify-center">
                        <Layers className="h-4 w-4 text-indigo-600" />
                      </div>
                      <div>
                        <p className="font-medium leading-none">{r.name}</p>
                        {r.description && <p className="text-xs text-muted-foreground line-clamp-1 max-w-[28ch]">{r.description}</p>}
                      </div>
                    </div>
                  </TableCell>
                  <TableCell className="font-mono text-xs bg-muted/20 rounded px-2 py-1 inline-block mt-2">{r.key}</TableCell>
                  <TableCell>
                    <FieldCountBadge recordTypeId={r.id} />
                  </TableCell>
                  <TableCell>
                    {r.isActive ? <Badge className="bg-emerald-50 text-emerald-700 border-emerald-200">Active</Badge> : <Badge variant="outline" className="text-muted-foreground">Inactive</Badge>}
                  </TableCell>
                  <TableCell className="hidden md:table-cell text-xs text-muted-foreground">{new Date(r.updatedAt).toLocaleDateString()}</TableCell>
                  <TableCell>
                    <div className="flex justify-end gap-1">
                      <Button variant="ghost" size="sm" onClick={() => router.push(`/settings/records/${r.id}`)}>
                        <Settings2 className="h-4 w-4 mr-1" /> Fields
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => openEdit(r)} aria-label="Edit">
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon" className="text-destructive hover:text-destructive" onClick={() => setToDelete(r)} aria-label="Delete">
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

      <RecordTypeDialog open={dialogOpen} onOpenChange={setDialogOpen} editing={editing} onSubmit={handleSubmit} isPending={isPending} />

      <AlertDialog open={!!toDelete} onOpenChange={() => setToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Archive record type?</AlertDialogTitle>
            <AlertDialogDescription>
              This will archive <strong>{toDelete?.name}</strong> (<span className="font-mono">{toDelete?.key}</span>). It will be hidden from normal use but historical records are preserved. This is a soft-delete and can be restored by support if needed.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive hover:bg-destructive/90"
              onClick={() => toDelete && deleteMutation.mutate(toDelete.id, { onSuccess: () => setToDelete(null) })}
            >
              Archive
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
