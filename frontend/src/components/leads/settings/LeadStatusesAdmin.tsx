"use client";

import { useState } from "react";
import { Plus, Pencil, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogFooter,
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
import { Field, FieldLabel } from "@/components/ui/field";
import { StatusBadge } from "../shared/StatusBadge";
import {
  useCreateLeadStatus,
  useDeleteLeadStatus,
  useLeadStatuses,
  useUpdateLeadStatus,
} from "@/lib/hooks/leads";
import { LeadStatusCreateRequest, LeadStatusSummary } from "@/types/leads";

const emptyForm: LeadStatusCreateRequest = {
  name: "",
  color: "#6366f1",
  displayOrder: 0,
  isDefault: false,
  isClosed: false,
};

export function LeadStatusesAdmin() {
  const { data: statuses, isLoading } = useLeadStatuses();
  const createMutation = useCreateLeadStatus();
  const updateMutation = useUpdateLeadStatus();
  const deleteMutation = useDeleteLeadStatus();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<LeadStatusSummary | null>(null);
  const [form, setForm] = useState<LeadStatusCreateRequest>(emptyForm);
  const [toDelete, setToDelete] = useState<LeadStatusSummary | null>(null);

  const sorted = [...(statuses || [])].sort(
    (a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)
  );

  function openCreate() {
    setEditing(null);
    setForm({ ...emptyForm, displayOrder: sorted.length });
    setDialogOpen(true);
  }

  function openEdit(status: LeadStatusSummary) {
    setEditing(status);
    setForm({
      name: status.name,
      color: status.color || "#6366f1",
      displayOrder: status.displayOrder ?? 0,
      isDefault: status.isDefault ?? false,
      isClosed: status.isClosed ?? false,
    });
    setDialogOpen(true);
  }

  function handleSave() {
    if (!form.name.trim()) return;
    if (editing) {
      updateMutation.mutate(
        { id: editing.id, data: form },
        { onSuccess: () => setDialogOpen(false) }
      );
    } else {
      createMutation.mutate(form, { onSuccess: () => setDialogOpen(false) });
    }
  }

  const isPending = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button onClick={openCreate}>
          <Plus className="mr-2 h-4 w-4" />
          Add Status
        </Button>
      </div>

      <div className="rounded-lg border bg-white overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Color</TableHead>
              <TableHead>Order</TableHead>
              <TableHead>Default</TableHead>
              <TableHead>Closed</TableHead>
              <TableHead className="w-[100px]" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center h-20">
                  Loading...
                </TableCell>
              </TableRow>
            ) : sorted.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center h-20 text-muted-foreground">
                  No statuses configured.
                </TableCell>
              </TableRow>
            ) : (
              sorted.map((status) => (
                <TableRow key={status.id}>
                  <TableCell>
                    <StatusBadge status={status} />
                  </TableCell>
                  <TableCell>
                    <span
                      className="inline-block h-4 w-4 rounded-full border"
                      style={{ backgroundColor: status.color || "#6366f1" }}
                    />
                  </TableCell>
                  <TableCell>{status.displayOrder ?? 0}</TableCell>
                  <TableCell>{status.isDefault ? "Yes" : "—"}</TableCell>
                  <TableCell>{status.isClosed ? "Yes" : "—"}</TableCell>
                  <TableCell>
                    <div className="flex gap-1">
                      <Button variant="ghost" size="icon" onClick={() => openEdit(status)}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="text-destructive"
                        onClick={() => setToDelete(status)}
                      >
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

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? "Edit Status" : "New Status"}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <Field>
              <FieldLabel>Name *</FieldLabel>
              <Input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="e.g. Qualified"
              />
            </Field>
            <Field>
              <FieldLabel>Color</FieldLabel>
              <div className="flex gap-2">
                <Input
                  type="color"
                  className="w-14 h-9 p-1"
                  value={form.color || "#6366f1"}
                  onChange={(e) => setForm({ ...form, color: e.target.value })}
                />
                <Input
                  value={form.color || ""}
                  onChange={(e) => setForm({ ...form, color: e.target.value })}
                  placeholder="#6366f1"
                />
              </div>
            </Field>
            <Field>
              <FieldLabel>Display order</FieldLabel>
              <Input
                type="number"
                value={form.displayOrder ?? 0}
                onChange={(e) =>
                  setForm({ ...form, displayOrder: Number(e.target.value) })
                }
              />
            </Field>
            <div className="flex items-center justify-between">
              <FieldLabel>Default status for new leads</FieldLabel>
              <Switch
                checked={form.isDefault ?? false}
                onCheckedChange={(v) => setForm({ ...form, isDefault: v })}
              />
            </div>
            <div className="flex items-center justify-between">
              <FieldLabel>Closed / won-lost stage</FieldLabel>
              <Switch
                checked={form.isClosed ?? false}
                onCheckedChange={(v) => setForm({ ...form, isClosed: v })}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleSave} disabled={isPending || !form.name.trim()}>
              {isPending ? "Saving..." : "Save"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={!!toDelete} onOpenChange={() => setToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete status?</AlertDialogTitle>
            <AlertDialogDescription>
              Leads using &quot;{toDelete?.name}&quot; may block deletion. This cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive hover:bg-destructive/90"
              onClick={() =>
                toDelete &&
                deleteMutation.mutate(toDelete.id, { onSuccess: () => setToDelete(null) })
              }
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
