"use client";

import { useState } from "react";
import { Plus, Pencil, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Field, FieldLabel } from "@/components/ui/field";
import { useDealStages, useCreateDealStage, useUpdateDealStage, useDeleteDealStage } from "@/lib/hooks/deals";
import { DealStageCreateRequest, DealStageSummary } from "@/types/deal-stages";

const emptyForm: DealStageCreateRequest = {
  name: "",
  color: "#3b82f6",
  displayOrder: 0,
  isDefault: false,
  isClosed: false,
};

export function DealStagesAdmin() {
  const { data: stages, isLoading } = useDealStages();
  const createMutation = useCreateDealStage();
  const updateMutation = useUpdateDealStage();
  const deleteMutation = useDeleteDealStage();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<DealStageSummary | null>(null);
  const [form, setForm] = useState<DealStageCreateRequest>(emptyForm);
  const [toDelete, setToDelete] = useState<DealStageSummary | null>(null);

  const sorted = [...(stages || [])].sort(
    (a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)
  );

  function openCreate() {
    setEditing(null);
    setForm({ ...emptyForm, displayOrder: sorted.length });
    setDialogOpen(true);
  }

  function openEdit(stage: DealStageSummary) {
    setEditing(stage);
    setForm({
      name: stage.name,
      color: stage.color || "#3b82f6",
      displayOrder: stage.displayOrder ?? 0,
      isDefault: stage.isDefault ?? false,
      isClosed: stage.isClosed ?? false,
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
          Add Stage
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
              <TableHead className="w-[100px] text-right">Actions</TableHead>
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
                  No stages configured.
                </TableCell>
              </TableRow>
            ) : (
              sorted.map((stage) => (
                <TableRow key={stage.id}>
                  <TableCell>
                    <span
                      className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold"
                      style={{
                        backgroundColor: `${stage.color || "#3b82f6"}15`,
                        color: stage.color || "#3b82f6",
                        border: `1px solid ${stage.color || "#3b82f6"}30`
                      }}
                    >
                      {stage.name}
                    </span>
                  </TableCell>
                  <TableCell>
                    <span
                      className="inline-block h-4 w-4 rounded-full border"
                      style={{ backgroundColor: stage.color || "#3b82f6" }}
                    />
                  </TableCell>
                  <TableCell>{stage.displayOrder ?? 0}</TableCell>
                  <TableCell>{stage.isDefault ? "Yes" : "—"}</TableCell>
                  <TableCell>{stage.isClosed ? "Yes" : "—"}</TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-1">
                      <Button variant="ghost" size="icon" onClick={() => openEdit(stage)}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="text-destructive"
                        onClick={() => setToDelete(stage)}
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
            <DialogTitle>{editing ? "Edit Stage" : "New Stage"}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <Field>
              <FieldLabel>Name *</FieldLabel>
              <Input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="e.g. Discovery"
              />
            </Field>
            <Field>
              <FieldLabel>Color</FieldLabel>
              <div className="flex gap-2">
                <Input
                  type="color"
                  className="w-14 h-9 p-1"
                  value={form.color || "#3b82f6"}
                  onChange={(e) => setForm({ ...form, color: e.target.value })}
                />
                <Input
                  value={form.color || ""}
                  onChange={(e) => setForm({ ...form, color: e.target.value })}
                  placeholder="#3b82f6"
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
              <FieldLabel>Default stage for new deals</FieldLabel>
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
            <AlertDialogTitle>Delete pipeline stage?</AlertDialogTitle>
            <AlertDialogDescription>
              Deals using &quot;{toDelete?.name}&quot; may block deletion. This cannot be undone.
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
