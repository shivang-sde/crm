"use client";

import { useState } from "react";
import { Plus, Pencil, Trash2, Loader2, Settings2, SearchX, Key, Database } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { useMappingProfiles, useCreateMappingProfile, useUpdateMappingProfile, useDeleteMappingProfile, useRecordTypes } from "@/lib/hooks/records";
import { RecordMappingProfileResponse } from "@/types/records";
import { MappingProfileDialog } from "./MappingProfileDialog";

export function MappingProfilesAdmin() {
  const [page] = useState(0);
  const [filter, setFilter] = useState("");
  const [recordTypeFilter, setRecordTypeFilter] = useState<string>("all");

  const { data: typesData } = useRecordTypes(0, 100);
  const types = typesData?.data ?? [];
  const typeMap = new Map(types.map((t) => [t.id, t]));

  const { data, isLoading, isError, error } = useMappingProfiles({ page, size: 50, recordTypeId: recordTypeFilter !== "all" ? recordTypeFilter : undefined });
  const createMutation = useCreateMappingProfile();
  const updateMutation = useUpdateMappingProfile();
  const deleteMutation = useDeleteMappingProfile();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<RecordMappingProfileResponse | null>(null);
  const [toDelete, setToDelete] = useState<RecordMappingProfileResponse | null>(null);

  const list = data?.data ?? [];
  const filtered = filter.trim()
    ? list.filter((m) => m.name.toLowerCase().includes(filter.toLowerCase()) || m.mappingKey.toLowerCase().includes(filter.toLowerCase()))
    : list;

  function openCreate() {
    setEditing(null);
    setDialogOpen(true);
  }
  function openEdit(m: RecordMappingProfileResponse) {
    setEditing(m);
    setDialogOpen(true);
  }
  function handleSubmit(data: any) {
    if (editing) {
      updateMutation.mutate(
        { id: editing.id, data: { name: data.name, description: data.description, mode: data.mode, configuration: data.configuration, isActive: data.isActive } },
        { onSuccess: () => setDialogOpen(false) }
      );
    } else {
      createMutation.mutate(data, { onSuccess: () => setDialogOpen(false) });
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
        <p className="text-sm text-destructive">Failed to load mapping profiles.</p>
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
          <Plus className="mr-2 h-4 w-4" /> New Mapping Profile
        </Button>
      </div>

      <div className="rounded-xl border bg-white overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40">
              <TableHead>Name</TableHead>
              <TableHead>Key</TableHead>
              <TableHead>Record Type</TableHead>
              <TableHead>Mode</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="w-[160px] text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filtered.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="h-64">
                  <div className="flex flex-col items-center justify-center gap-3 py-6 text-center">
                    <div className="rounded-full bg-muted p-4">
                      <Settings2 className="h-8 w-8 text-muted-foreground" />
                    </div>
                    <div>
                      <p className="font-medium">No Mapping Profiles yet</p>
                      <p className="text-sm text-muted-foreground max-w-sm">Create a mapping to transform external payloads into your canonical Record Type. Example: Sellspark CDR → CDR.</p>
                    </div>
                    <Button onClick={openCreate} size="sm">
                      <Plus className="mr-2 h-4 w-4" /> Create Mapping Profile
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((m) => {
                const rt = typeMap.get(m.recordTypeId);
                return (
                  <TableRow key={m.id} className="hover:bg-muted/20">
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <div className="h-8 w-8 rounded-lg bg-indigo-50 flex items-center justify-center">
                          <Key className="h-4 w-4 text-indigo-600" />
                        </div>
                        <div>
                          <p className="font-medium leading-none">{m.name}</p>
                          {m.description && <p className="text-xs text-muted-foreground line-clamp-1 max-w-[28ch]">{m.description}</p>}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="font-mono text-xs">{m.mappingKey}</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-1">
                        <Database className="h-3 w-3 text-muted-foreground" />
                        <span className="text-sm">{m.recordTypeName || rt?.name || "—"}</span>
                        {m.recordTypeKey && <span className="font-mono text-xs text-muted-foreground">({m.recordTypeKey})</span>}
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant={m.mode === "CUSTOM" ? "secondary" : "outline"}>{m.mode}</Badge>
                    </TableCell>
                    <TableCell>{m.isActive ? <Badge className="bg-emerald-50 text-emerald-700 border-emerald-200">Active</Badge> : <Badge variant="outline">Inactive</Badge>}</TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" onClick={() => openEdit(m)} aria-label="Edit">
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button variant="ghost" size="icon" className="text-destructive hover:text-destructive" onClick={() => setToDelete(m)} aria-label="Delete">
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

      <MappingProfileDialog open={dialogOpen} onOpenChange={setDialogOpen} editing={editing} onSubmit={handleSubmit} isPending={isPending} />

      <AlertDialog open={!!toDelete} onOpenChange={() => setToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Archive mapping profile?</AlertDialogTitle>
            <AlertDialogDescription>
              This will archive <strong>{toDelete?.name}</strong> (<span className="font-mono">{toDelete?.mappingKey}</span>). It will be hidden from normal use but preserved for history (soft-delete).
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
    </div>
  );
}
