"use client";

import { useState } from "react";
import { Plus, Pencil, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
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
import {
  useCreateDealCustomField,
  useDeleteDealCustomField,
  useDealCustomFields,
  useUpdateDealCustomField,
} from "@/lib/hooks/deals";
import {
  DealCustomFieldCreateRequest,
  DealCustomFieldResponse,
  DealFieldType,
} from "@/types/deal-custom-fields";

const FIELD_TYPES: DealFieldType[] = [
  "TEXT",
  "TEXTAREA",
  "NUMBER",
  "EMAIL",
  "PHONE",
  "DATE",
  "BOOLEAN",
  "SELECT",
  "MULTISELECT",
  "URL",
];

const OPTION_TYPES: DealFieldType[] = ["SELECT", "MULTISELECT"];

function slugify(label: string) {
  return label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_|_$/g, "");
}

const emptyForm: DealCustomFieldCreateRequest = {
  fieldKey: "",
  fieldLabel: "",
  fieldType: "TEXT",
  isRequired: false,
  isActive: true,
  displayOrder: 0,
  options: [],
};

export function DealCustomFieldsAdmin() {
  const { data: fields, isLoading } = useDealCustomFields();
  const createMutation = useCreateDealCustomField();
  const updateMutation = useUpdateDealCustomField();
  const deleteMutation = useDeleteDealCustomField();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<DealCustomFieldResponse | null>(null);
  const [form, setForm] = useState<DealCustomFieldCreateRequest>(emptyForm);
  const [toDelete, setToDelete] = useState<DealCustomFieldResponse | null>(null);

  const sorted = [...(fields || [])].sort(
    (a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)
  );

  const showOptions = OPTION_TYPES.includes(form.fieldType);

  function openCreate() {
    setEditing(null);
    setForm({ ...emptyForm, displayOrder: sorted.length });
    setDialogOpen(true);
  }

  function openEdit(field: DealCustomFieldResponse) {
    setEditing(field);
    setForm({
      fieldKey: field.fieldKey,
      fieldLabel: field.fieldLabel,
      fieldType: field.fieldType,
      isRequired: field.isRequired,
      isActive: field.isActive,
      displayOrder: field.displayOrder,
      options: field.options ? [...field.options] : [],
    });
    setDialogOpen(true);
  }

  function handleSave() {
    if (!form.fieldLabel.trim() || !form.fieldKey.trim()) return;
    const payload = {
      ...form,
      options: showOptions ? form.options : undefined,
    };
    if (editing) {
      updateMutation.mutate(
        { id: editing.id, data: payload },
        { onSuccess: () => setDialogOpen(false) }
      );
    } else {
      createMutation.mutate(payload, { onSuccess: () => setDialogOpen(false) });
    }
  }

  function addOption() {
    setForm({
      ...form,
      options: [...(form.options || []), { label: "", value: "" }],
    });
  }

  function updateOption(index: number, key: "label" | "value", value: string) {
    const options = [...(form.options || [])];
    options[index] = { ...options[index], [key]: value };
    if (key === "label" && !editing) {
      options[index].value = slugify(value) || options[index].value;
    }
    setForm({ ...form, options });
  }

  function removeOption(index: number) {
    setForm({
      ...form,
      options: (form.options || []).filter((_, i) => i !== index),
    });
  }

  const isPending = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button onClick={openCreate}>
          <Plus className="mr-2 h-4 w-4" />
          Add Field
        </Button>
      </div>

      <div className="rounded-lg border bg-white overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Label</TableHead>
              <TableHead>Key</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Required</TableHead>
              <TableHead>Active</TableHead>
              <TableHead>Order</TableHead>
              <TableHead className="w-[100px] text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={7} className="text-center h-20">
                  Loading...
                </TableCell>
              </TableRow>
            ) : sorted.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="text-center h-20 text-muted-foreground">
                  No custom fields configured.
                </TableCell>
              </TableRow>
            ) : (
              sorted.map((field) => (
                <TableRow key={field.id}>
                  <TableCell className="font-medium">{field.fieldLabel}</TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground">{field.fieldKey}</TableCell>
                  <TableCell>
                    <Badge variant="outline">{field.fieldType}</Badge>
                  </TableCell>
                  <TableCell>{field.isRequired ? "Yes" : "—"}</TableCell>
                  <TableCell>{field.isActive ? "Yes" : "—"}</TableCell>
                  <TableCell>{field.displayOrder}</TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-1">
                      <Button variant="ghost" size="icon" onClick={() => openEdit(field)}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="text-destructive"
                        onClick={() => setToDelete(field)}
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

      {/* Create / Edit Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editing ? "Edit Field" : "New Custom Field"}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <Field>
              <FieldLabel>Label *</FieldLabel>
              <Input
                value={form.fieldLabel}
                onChange={(e) => {
                  const label = e.target.value;
                  setForm((prev) => ({
                    ...prev,
                    fieldLabel: label,
                    fieldKey: editing ? prev.fieldKey : slugify(label),
                  }));
                }}
                placeholder="e.g. Deal Source"
              />
            </Field>
            <Field>
              <FieldLabel>Field key *</FieldLabel>
              <Input
                value={form.fieldKey}
                onChange={(e) => setForm({ ...form, fieldKey: e.target.value })}
                placeholder="deal_source"
                disabled={!!editing}
                className="font-mono text-sm"
              />
            </Field>
            <Field>
              <FieldLabel>Type *</FieldLabel>
              <Select
                value={form.fieldType}
                onValueChange={(v) =>
                  setForm({
                    ...form,
                    fieldType: v as DealFieldType,
                    options: OPTION_TYPES.includes(v as DealFieldType)
                      ? form.options?.length
                        ? form.options
                        : [{ label: "", value: "" }]
                      : [],
                  })
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {FIELD_TYPES.map((t) => (
                    <SelectItem key={t} value={t}>
                      {t}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
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
              <FieldLabel>Required</FieldLabel>
              <Switch
                checked={form.isRequired ?? false}
                onCheckedChange={(v) => setForm({ ...form, isRequired: v })}
              />
            </div>
            <div className="flex items-center justify-between">
              <FieldLabel>Active</FieldLabel>
              <Switch
                checked={form.isActive ?? true}
                onCheckedChange={(v) => setForm({ ...form, isActive: v })}
              />
            </div>

            {showOptions && (
              <div className="space-y-2 border-t pt-4">
                <div className="flex items-center justify-between">
                  <FieldLabel>Options</FieldLabel>
                  <Button type="button" variant="outline" size="sm" onClick={addOption}>
                    <Plus className="h-3 w-3 mr-1" />
                    Add
                  </Button>
                </div>
                {(form.options || []).map((opt, i) => (
                  <div key={i} className="flex gap-2 items-center">
                    <Input
                      placeholder="Label"
                      value={opt.label}
                      onChange={(e) => updateOption(i, "label", e.target.value)}
                    />
                    <Input
                      placeholder="value"
                      className="font-mono text-sm"
                      value={opt.value}
                      onChange={(e) => updateOption(i, "value", e.target.value)}
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() => removeOption(i)}
                    >
                      <X className="h-4 w-4" />
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={handleSave}
              disabled={isPending || !form.fieldLabel.trim() || !form.fieldKey.trim()}
            >
              {isPending ? "Saving..." : "Save"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation */}
      <AlertDialog open={!!toDelete} onOpenChange={() => setToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete custom field?</AlertDialogTitle>
            <AlertDialogDescription>
              Remove &quot;{toDelete?.fieldLabel}&quot;? Existing deal data for this key will remain in JSONB.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive hover:bg-destructive/90"
              onClick={() =>
                toDelete &&
                deleteMutation.mutate(toDelete.id, {
                  onSuccess: () => setToDelete(null),
                })
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
