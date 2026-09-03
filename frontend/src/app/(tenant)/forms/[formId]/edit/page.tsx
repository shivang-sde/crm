"use client";

import { useEffect, useState, useMemo } from "react";
import { useParams, useRouter } from "next/navigation";
import { toast } from "sonner";
import {
  ArrowLeft,
  Plus,
  Trash2,
  Copy,
  GripVertical,
  Eye,
  Save,
  Rocket,
  Ban,
  AlertTriangle,
  Settings,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
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

import { useForm, useUpdateForm, usePublishForm, useUnpublishForm } from "@/lib/hooks/forms";
import { useLeadIngestionTargetFields } from "@/lib/hooks/acquisition";
import type { FormField, FormFieldType } from "@/types/forms";

const palette: { type: FormFieldType; label: string; desc: string }[] = [
  { type: "TEXT", label: "Text", desc: "Single line" },
  { type: "TEXTAREA", label: "Textarea", desc: "Multi-line" },
  { type: "EMAIL", label: "Email", desc: "Email address" },
  { type: "PHONE", label: "Phone", desc: "Phone number" },
  { type: "NUMBER", label: "Number", desc: "Numeric" },
  { type: "SELECT", label: "Select", desc: "Dropdown" },
  { type: "RADIO", label: "Radio", desc: "Single choice" },
  { type: "CHECKBOX", label: "Checkbox", desc: "Boolean" },
  { type: "MULTISELECT", label: "Multi-select", desc: "Multiple choice" },
  { type: "DATE", label: "Date", desc: "Date picker" },
  { type: "URL", label: "URL", desc: "Website" },
  { type: "HIDDEN", label: "Hidden", desc: "Hidden field" },
];

function toKey(label: string, existing: Set<string>) {
  let base = label
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 30);
  if (!base) base = "field";
  let key = base;
  let i = 1;
  while (existing.has(key)) {
    key = `${base}_${i++}`;
  }
  return key;
}

export default function FormBuilderPage() {
  const params = useParams<{ formId: string }>();
  const formId = params?.formId ?? "";
  const router = useRouter();

  const { data, isLoading, isError, refetch } = useForm(formId);
  const updateMut = useUpdateForm();
  const publishMut = usePublishForm();
  const unpublishMut = useUnpublishForm();

  const [formName, setFormName] = useState("");
  const [formDesc, setFormDesc] = useState("");
  const [fields, setFields] = useState<FormField[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [hasUnsaved, setHasUnsaved] = useState(false);
  const [submitLabel, setSubmitLabel] = useState("Submit");
  const [successMessage, setSuccessMessage] = useState("Thanks! We'll contact you shortly.");

  const form = data as unknown as { id: string; name: string; description?: string; status: string; publicKey?: string; settings?: Record<string, unknown>; fields: FormField[] } | undefined;

  // Use first form's tenant for target fields? For now, use acquisition target fields via dummy configId
  // Instead, we can fetch target fields via a dummy - but we need tenant target fields. We'll use a hook that fetches for any config? For simplicity, fetch via acquisition target fields with a dummy.
  // We'll create a small fetch for target fields using the form's acquisitionConfigId if available, else fallback to first acquisition config.
  // For now, we’ll use a direct fetch to /acquisition/configs to get target fields via existing hook with a placeholder.
  // Simplify: use static target fields list for builder (standard + custom will be fetched via API in mapping step)
  // For MVP, we’ll allow manual target field entry and also fetch via existing target fields hook using a dummy.

  useEffect(() => {
    if (form) {
      setFormName(form.name ?? "");
      setFormDesc((form as unknown as { description?: string }).description ?? "");
      setFields((form.fields ?? []).slice().sort((a, b) => a.orderIndex - b.orderIndex));
      const settings = (form as unknown as { settings?: Record<string, unknown> }).settings ?? {};
      setSubmitLabel(String(settings.submitButtonLabel ?? "Submit"));
      setSuccessMessage(String(settings.successMessage ?? "Thanks! We'll contact you shortly."));
      setHasUnsaved(false);
    }
  }, [form]);

  const selectedField = useMemo(() => fields.find((f) => f.id === selectedId) ?? null, [fields, selectedId]);

  const markDirty = () => setHasUnsaved(true);

  const addField = (type: FormFieldType) => {
    const existingKeys = new Set(fields.map((f) => f.fieldKey));
    const label = type === "EMAIL" ? "Business Email" : type === "PHONE" ? "Phone" : type === "TEXT" ? "First Name" : type;
    const key = toKey(label, existingKeys);
    const newField: FormField = {
      id: `tmp_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
      fieldKey: key,
      type,
      label,
      placeholder: "",
      helpText: "",
      required: false,
      orderIndex: fields.length,
      defaultValue: null,
      options: type === "SELECT" || type === "RADIO" || type === "MULTISELECT" ? [{ label: "Option 1", value: "option_1" }] : null,
      crmTargetType: null,
      crmTargetField: null,
      transformType: "NONE",
      transformConfig: null,
    };
    setFields((prev) => [...prev, newField]);
    setSelectedId(newField.id);
    markDirty();
  };

  const updateField = (id: string, patch: Partial<FormField>) => {
    setFields((prev) => prev.map((f) => (f.id === id ? { ...f, ...patch } : f)));
    markDirty();
  };

  const deleteField = (id: string) => {
    setFields((prev) => prev.filter((f) => f.id !== id).map((f, idx) => ({ ...f, orderIndex: idx })));
    if (selectedId === id) setSelectedId(null);
    markDirty();
  };

  const duplicateField = (id: string) => {
    const field = fields.find((f) => f.id === id);
    if (!field) return;
    const existingKeys = new Set(fields.map((f) => f.fieldKey));
    const newKey = toKey(field.fieldKey + "_copy", existingKeys);
    const clone: FormField = {
      ...field,
      id: `tmp_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
      fieldKey: newKey,
      label: field.label + " Copy",
      orderIndex: fields.length,
    };
    setFields((prev) => [...prev, clone]);
    setSelectedId(clone.id);
    markDirty();
  };

  const moveField = (id: string, dir: -1 | 1) => {
    const idx = fields.findIndex((f) => f.id === id);
    if (idx < 0) return;
    const newIdx = idx + dir;
    if (newIdx < 0 || newIdx >= fields.length) return;
    const copy = [...fields];
    const [moved] = copy.splice(idx, 1);
    copy.splice(newIdx, 0, moved);
    setFields(copy.map((f, i) => ({ ...f, orderIndex: i })));
    markDirty();
  };

  const handleSave = async () => {
    if (!formName.trim()) {
      toast.error("Form name is required");
      return;
    }
    // Validate duplicate keys, empty label
    const keys = fields.map((f) => f.fieldKey);
    if (new Set(keys).size !== keys.length) {
      toast.error("Duplicate field keys");
      return;
    }
    for (const f of fields) {
      if (!f.label?.trim()) {
        toast.error(`Field ${f.fieldKey} requires a label`);
        return;
      }
      if ((f.type === "SELECT" || f.type === "RADIO" || f.type === "MULTISELECT") && (!f.options || f.options.length === 0)) {
        toast.error(`Field ${f.label} requires options`);
        return;
      }
    }

    try {
      await updateMut.mutateAsync({
        id: formId,
        data: {
          name: formName.trim(),
          description: formDesc.trim() || undefined,
          settings: { submitButtonLabel: submitLabel, successMessage },
          fields: fields.map((f, idx) => ({
            id: f.id.startsWith("tmp_") ? undefined : f.id,
            fieldKey: f.fieldKey,
            type: f.type,
            label: f.label,
            placeholder: f.placeholder ?? undefined,
            helpText: f.helpText ?? undefined,
            required: f.required ?? false,
            orderIndex: idx,
            defaultValue: f.defaultValue ?? undefined,
            options: f.options ?? undefined,
            crmTargetType: f.crmTargetType ?? undefined,
            crmTargetField: f.crmTargetField ?? undefined,
            transformType: f.transformType ?? "NONE",
            transformConfig: f.transformConfig ?? undefined,
          })),
        },
      });
      toast.success("Draft saved");
      setHasUnsaved(false);
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message ?? "Save failed";
      toast.error(msg);
    }
  };

  const handlePublish = async () => {
    // Save first if dirty
    if (hasUnsaved) {
      toast.error("Save draft before publishing");
      return;
    }
    try {
      await publishMut.mutateAsync(formId);
      toast.success("Form published");
      refetch();
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message ?? "Publish failed";
      toast.error(msg);
    }
  };

  const handleUnpublish = async () => {
    try {
      await unpublishMut.mutateAsync(formId);
      toast.success("Form unpublished");
      refetch();
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message ?? "Unpublish failed";
      toast.error(msg);
    }
  };

  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (hasUnsaved) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [hasUnsaved]);

  if (isLoading) {
    return (
      <div className="p-6">
        <p className="text-sm text-muted-foreground">Loading form…</p>
      </div>
    );
  }

  if (isError || !form) {
    return (
      <div className="p-6 space-y-3">
        <p className="text-sm text-red-600">Form not found.</p>
        <button onClick={() => refetch()} className="text-sm underline">
          Retry
        </button>
      </div>
    );
  }

  const isPublished = form.status === "PUBLISHED";

  return (
    <div className="flex flex-col h-[calc(100vh-4rem)]">
      {/* Header */}
      <div className="flex items-center justify-between border-b px-4 py-3 bg-background">
        <div className="flex items-center gap-3 min-w-0">
          <button onClick={() => router.push("/forms")} className="text-sm text-muted-foreground hover:text-foreground">
            ← Forms
          </button>
          <input
            value={formName}
            onChange={(e) => {
              setFormName(e.target.value);
              markDirty();
            }}
            className="text-lg font-semibold bg-transparent border-none focus:outline-none focus:ring-1 focus:ring-ring rounded px-1"
            placeholder="Form name"
          />
          <span className={`text-xs px-2 py-0.5 rounded-full border ${isPublished ? "bg-green-100 text-green-700 border-green-200" : "bg-muted"}`}>
            {form.status}
          </span>
          {hasUnsaved && <span className="text-xs text-amber-600">Unsaved changes</span>}
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => setPreviewOpen(true)}>
            <Eye className="mr-1 h-4 w-4" /> Preview
          </Button>
          <Button variant="outline" size="sm" onClick={handleSave} disabled={updateMut.isPending}>
            <Save className="mr-1 h-4 w-4" /> {updateMut.isPending ? "Saving…" : "Save Draft"}
          </Button>
          {isPublished ? (
            <Button variant="outline" size="sm" onClick={handleUnpublish} disabled={unpublishMut.isPending}>
              <Ban className="mr-1 h-4 w-4" /> Unpublish
            </Button>
          ) : (
            <Button size="sm" onClick={handlePublish} disabled={publishMut.isPending}>
              <Rocket className="mr-1 h-4 w-4" /> Publish
            </Button>
          )}
        </div>
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* Palette */}
        <div className="w-[200px] border-r bg-muted/20 p-3 overflow-auto">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-2">Field Palette</p>
          <div className="space-y-2">
            {[
              { group: "Basic", items: palette.filter((p) => ["TEXT", "TEXTAREA", "EMAIL", "PHONE", "NUMBER"].includes(p.type)) },
              { group: "Choice", items: palette.filter((p) => ["SELECT", "RADIO", "CHECKBOX", "MULTISELECT"].includes(p.type)) },
              { group: "Other", items: palette.filter((p) => ["DATE", "URL", "HIDDEN"].includes(p.type)) },
            ].map((g) => (
              <div key={g.group}>
                <p className="text-xs font-medium text-muted-foreground mt-2">{g.group}</p>
                {g.items.map((item) => (
                  <button
                    key={item.type}
                    onClick={() => addField(item.type)}
                    className="w-full text-left rounded-md border bg-background p-2 hover:bg-muted mt-1"
                  >
                    <p className="text-sm font-medium">{item.label}</p>
                    <p className="text-xs text-muted-foreground">{item.desc}</p>
                  </button>
                ))}
              </div>
            ))}
          </div>
        </div>

        {/* Canvas */}
        <div className="flex-1 overflow-auto p-4 bg-muted/10">
          <Card className="max-w-2xl mx-auto">
            <CardHeader>
              <CardTitle>{formName || "Untitled Form"}</CardTitle>
              <CardDescription>{formDesc || "Fill the form below."}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {fields.length === 0 ? (
                <div className="rounded-md border border-dashed p-8 text-center">
                  <p className="font-medium">Your form is empty.</p>
                  <p className="text-sm text-muted-foreground">Add your first field from the palette to start collecting leads.</p>
                  <Button size="sm" className="mt-3" onClick={() => addField("TEXT")}>
                    <Plus className="mr-1 h-4 w-4" /> Add field
                  </Button>
                </div>
              ) : (
                fields
                  .slice()
                  .sort((a, b) => a.orderIndex - b.orderIndex)
                  .map((field) => (
                    <div
                      key={field.id}
                      onClick={() => setSelectedId(field.id)}
                      className={`rounded-md border p-3 cursor-pointer ${selectedId === field.id ? "border-primary bg-primary/5" : "bg-background hover:bg-muted/30"}`}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex items-center gap-2">
                          <GripVertical className="h-4 w-4 text-muted-foreground" />
                          <div>
                            <p className="text-sm font-medium">
                              {field.label}
                              {field.required && <span className="text-red-600 ml-1">*</span>}
                            </p>
                            <p className="text-xs text-muted-foreground">
                              {field.fieldKey} · {field.type} {field.crmTargetField ? `→ ${field.crmTargetType}:${field.crmTargetField}` : "· not mapped"}
                            </p>
                          </div>
                        </div>
                        <div className="flex items-center gap-1">
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-6 w-6"
                            onClick={(e) => {
                              e.stopPropagation();
                              moveField(field.id, -1);
                            }}
                          >
                            ↑
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-6 w-6"
                            onClick={(e) => {
                              e.stopPropagation();
                              moveField(field.id, 1);
                            }}
                          >
                            ↓
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-6 w-6"
                            onClick={(e) => {
                              e.stopPropagation();
                              duplicateField(field.id);
                            }}
                          >
                            <Copy className="h-3 w-3" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-6 w-6"
                            onClick={(e) => {
                              e.stopPropagation();
                              setDeleteConfirm(field.id);
                            }}
                          >
                            <Trash2 className="h-3 w-3" />
                          </Button>
                        </div>
                      </div>
                      {/* Preview of field */}
                      <div className="mt-2">
                        {field.type === "TEXTAREA" ? (
                          <div className="h-16 rounded-md border bg-muted/20" />
                        ) : field.type === "SELECT" || field.type === "RADIO" ? (
                          <div className="text-xs text-muted-foreground">
                            Options: {(field.options ?? []).map((o) => o.label).join(", ") || "No options"}
                          </div>
                        ) : field.type === "HIDDEN" ? (
                          <p className="text-xs text-muted-foreground">Hidden: {field.defaultValue ?? "—"}</p>
                        ) : (
                          <div className="h-8 rounded-md border bg-muted/20 flex items-center px-2 text-xs text-muted-foreground">
                            {field.placeholder || field.type}
                          </div>
                        )}
                      </div>
                    </div>
                  ))
              )}
              <Button variant="outline" className="w-full mt-2" disabled>
                {submitLabel || "Submit"}
              </Button>
              {isPublished && (form as unknown as { publicKey?: string }).publicKey && (
                <div className="rounded-md border bg-muted/20 p-2 space-y-1">
                  <p className="text-xs font-medium">Published — Public URL</p>
                  <code className="text-xs break-all">{typeof window !== "undefined" ? `${window.location.origin}/forms/public/${(form as unknown as { publicKey: string }).publicKey}` : ""}</code>
                  <p className="text-xs font-medium mt-2">Embed (iframe)</p>
                  <pre className="text-xs bg-background border rounded p-2 overflow-auto max-h-32 whitespace-pre-wrap break-all">
                    {`<iframe src="${typeof window !== "undefined" ? window.location.origin : ""}/forms/public/${(form as unknown as { publicKey: string }).publicKey}" width="100%" height="600" frameborder="0" loading="lazy" style="border:0;"></iframe>`}
                  </pre>
                  <p className="text-xs text-muted-foreground">Copy the iframe to embed on an external site. Auto-resize via postMessage is supported.</p>
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Properties */}
        <div className="w-[300px] border-l bg-background p-3 overflow-auto">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-2">Field Settings</p>
          {!selectedField ? (
            <div className="space-y-3">
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm">Form Settings</CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                  <div className="space-y-1">
                    <label className="text-xs font-medium">Form Name</label>
                    <input
                      className="w-full rounded-md border px-2 py-1 text-sm"
                      value={formName}
                      onChange={(e) => {
                        setFormName(e.target.value);
                        markDirty();
                      }}
                    />
                  </div>
                  <div className="space-y-1">
                    <label className="text-xs font-medium">Description</label>
                    <textarea
                      className="w-full rounded-md border px-2 py-1 text-sm"
                      rows={3}
                      value={formDesc}
                      onChange={(e) => {
                        setFormDesc(e.target.value);
                        markDirty();
                      }}
                    />
                  </div>
                  <div className="space-y-1">
                    <label className="text-xs font-medium">Submit label</label>
                    <input
                      className="w-full rounded-md border px-2 py-1 text-sm"
                      value={submitLabel}
                      onChange={(e) => {
                        setSubmitLabel(e.target.value);
                        markDirty();
                      }}
                    />
                  </div>
                  <div className="space-y-1">
                    <label className="text-xs font-medium">Success message</label>
                    <textarea
                      className="w-full rounded-md border px-2 py-1 text-sm"
                      rows={2}
                      value={successMessage}
                      onChange={(e) => {
                        setSuccessMessage(e.target.value);
                        markDirty();
                      }}
                    />
                  </div>
                  <p className="text-xs text-muted-foreground">Select a field to configure its properties, or add a field from the palette.</p>
                </CardContent>
              </Card>
            </div>
          ) : (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium">Configure: {selectedField.label}</p>
                <Button variant="ghost" size="sm" onClick={() => setSelectedId(null)}>
                  ×
                </Button>
              </div>

              <div className="space-y-2">
                <label className="text-xs font-medium">Label *</label>
                <input
                  className="w-full rounded-md border px-2 py-1 text-sm"
                  value={selectedField.label}
                  onChange={(e) => updateField(selectedField.id, { label: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <label className="text-xs font-medium">Field Key *</label>
                <input
                  className="w-full rounded-md border px-2 py-1 text-sm font-mono"
                  value={selectedField.fieldKey}
                  onChange={(e) => updateField(selectedField.id, { fieldKey: e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, "_") })}
                />
                <p className="text-xs text-muted-foreground">Stable key, e.g., business_email. Used as submission payload key.</p>
              </div>
              <div className="space-y-2">
                <label className="text-xs font-medium">Type</label>
                <select
                  className="w-full rounded-md border px-2 py-1 text-sm"
                  value={selectedField.type}
                  onChange={(e) => updateField(selectedField.id, { type: e.target.value as FormFieldType })}
                >
                  {palette.map((p) => (
                    <option key={p.type} value={p.type}>
                      {p.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <label className="text-xs font-medium">Placeholder</label>
                <input
                  className="w-full rounded-md border px-2 py-1 text-sm"
                  value={selectedField.placeholder ?? ""}
                  onChange={(e) => updateField(selectedField.id, { placeholder: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <label className="text-xs font-medium">Help text</label>
                <input
                  className="w-full rounded-md border px-2 py-1 text-sm"
                  value={selectedField.helpText ?? ""}
                  onChange={(e) => updateField(selectedField.id, { helpText: e.target.value })}
                />
              </div>
              <div className="flex items-center gap-2">
                <input
                  id={`req-${selectedField.id}`}
                  type="checkbox"
                  checked={!!selectedField.required}
                  onChange={(e) => updateField(selectedField.id, { required: e.target.checked })}
                />
                <label htmlFor={`req-${selectedField.id}`} className="text-xs font-medium">
                  Required
                </label>
              </div>

              {(selectedField.type === "SELECT" || selectedField.type === "RADIO" || selectedField.type === "MULTISELECT") && (
                <div className="space-y-2">
                  <label className="text-xs font-medium">Options (one per line: label|value)</label>
                  <textarea
                    className="w-full rounded-md border px-2 py-1 text-sm font-mono"
                    rows={4}
                    value={(selectedField.options ?? []).map((o) => `${o.label}|${o.value}`).join("\n")}
                    onChange={(e) => {
                      const lines = e.target.value.split("\n").filter(Boolean);
                      const opts = lines.map((line) => {
                        const [label, value] = line.split("|");
                        return { label: label?.trim() ?? "", value: (value ?? label ?? "").trim() };
                      });
                      updateField(selectedField.id, { options: opts });
                    }}
                    placeholder="India|india&#10;USA|usa"
                  />
                </div>
              )}

              {selectedField.type === "HIDDEN" && (
                <div className="space-y-1">
                  <label className="text-xs font-medium">Default value (hidden)</label>
                  <input
                    className="w-full rounded-md border px-2 py-1 text-sm"
                    value={selectedField.defaultValue ?? ""}
                    onChange={(e) => updateField(selectedField.id, { defaultValue: e.target.value })}
                  />
                </div>
              )}

              <div className="space-y-1">
                <label className="text-xs font-medium">CRM Field</label>
                <select
                  className="w-full rounded-md border px-2 py-1 text-sm"
                  value={selectedField.crmTargetField ? `${selectedField.crmTargetType}:${selectedField.crmTargetField}` : ""}
                  onChange={(e) => {
                    const val = e.target.value;
                    if (!val) {
                      updateField(selectedField.id, { crmTargetType: null, crmTargetField: null });
                    } else {
                      const [t, f] = val.split(":");
                      updateField(selectedField.id, { crmTargetType: t, crmTargetField: f });
                    }
                  }}
                >
                  <option value="">— Not mapped —</option>
                  <option value="STANDARD_FIELD:firstName">Lead → First Name</option>
                  <option value="STANDARD_FIELD:lastName">Lead → Last Name</option>
                  <option value="STANDARD_FIELD:email">Lead → Email</option>
                  <option value="STANDARD_FIELD:phone">Lead → Phone</option>
                  <option value="STANDARD_FIELD:company">Lead → Company</option>
                  <option value="SYSTEM_FIELD:source">Lead → Source</option>
                  <option value="SYSTEM_FIELD:status">Lead → Status</option>
                  {/* Custom fields would be listed here via API; for MVP show manual entry */}
                </select>
                <p className="text-xs text-muted-foreground">
                  Maps form value <code>{selectedField.fieldKey}</code> → CRM. Transform via mapping.
                </p>
                {!selectedField.crmTargetField && (
                  <p className="text-xs text-amber-600 flex items-center gap-1">
                    <AlertTriangle className="h-3 w-3" /> Not mapped — submission will not populate CRM for this field.
                  </p>
                )}
              </div>

              <div className="space-y-1">
                <label className="text-xs font-medium">Transform</label>
                <select
                  className="w-full rounded-md border px-2 py-1 text-sm"
                  value={selectedField.transformType ?? "NONE"}
                  onChange={(e) => updateField(selectedField.id, { transformType: e.target.value })}
                >
                  <option value="NONE">None</option>
                  <option value="TRIM">Trim</option>
                  <option value="LOWERCASE">Lowercase</option>
                  <option value="UPPERCASE">Uppercase</option>
                </select>
              </div>
            </div>
          )}
        </div>
      </div>

      <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
        <DialogContent className="max-w-lg max-h-[80vh] overflow-auto">
          <DialogHeader>
            <DialogTitle>Preview — {formName}</DialogTitle>
            <DialogDescription>As visitor would see it. Submit is disabled in preview.</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            {fields.length === 0 ? (
              <p className="text-sm text-muted-foreground">No fields to preview.</p>
            ) : (
              fields
                .slice()
                .sort((a, b) => a.orderIndex - b.orderIndex)
                .map((f) => (
                  <div key={f.id} className="space-y-1">
                    <label className="text-sm font-medium">
                      {f.label}
                      {f.required && <span className="text-red-600 ml-1">*</span>}
                    </label>
                    {f.helpText && <p className="text-xs text-muted-foreground">{f.helpText}</p>}
                    {f.type === "TEXTAREA" ? (
                      <div className="h-16 rounded-md border bg-muted/20" />
                    ) : f.type === "HIDDEN" ? (
                      <p className="text-xs text-muted-foreground">Hidden: {f.defaultValue ?? "—"}</p>
                    ) : (
                      <div className="h-8 rounded-md border bg-muted/20" />
                    )}
                  </div>
                ))
            )}
            <Button className="w-full" disabled>
              {submitLabel}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <AlertDialog open={!!deleteConfirm} onOpenChange={(o) => !o && setDeleteConfirm(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove field?</AlertDialogTitle>
            <AlertDialogDescription>
              This removes the field from the form. The CRM mapping will also no longer be used by this form.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                if (deleteConfirm) deleteField(deleteConfirm);
                setDeleteConfirm(null);
              }}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Remove
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
