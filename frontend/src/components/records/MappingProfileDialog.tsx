"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Field, FieldLabel, FieldError } from "@/components/ui/field";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useRecordTypes, useRecordFields } from "@/lib/hooks/records";
import { RecordMappingProfileResponse, MappingProfileMode, MappingEntry, MappingTransform } from "@/types/records";
import { Plus, Trash2, Eye } from "lucide-react";
import { recordMappingApi } from "@/lib/api/records";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editing: RecordMappingProfileResponse | null;
  onSubmit: (data: {
    recordTypeId: string;
    mappingKey: string;
    name: string;
    description?: string;
    mode: MappingProfileMode;
    configuration?: { mappings?: MappingEntry[] };
    isActive: boolean;
  }) => void;
  isPending: boolean;
}

function slugify(label: string) {
  return label.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "").replace(/__+/g, "_");
}
const KEY_REGEX = /^[a-z][a-z0-9_]*$/;

export function MappingProfileDialog({ open, onOpenChange, editing, onSubmit, isPending }: Props) {
  const { data: typesData } = useRecordTypes(0, 100);
  const types = typesData?.data?.filter((t) => t.isActive) ?? [];

  const [recordTypeId, setRecordTypeId] = useState("");
  const [mappingKey, setMappingKey] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [mode, setMode] = useState<MappingProfileMode>("CUSTOM");
  const [isActive, setIsActive] = useState(true);
  const [mappings, setMappings] = useState<MappingEntry[]>([]);
  const [touched, setTouched] = useState(false);
  const [sampleJson, setSampleJson] = useState('{\n  "callId": "abc123",\n  "mobile": "9876543210",\n  "durationSec": 120\n}');
  const [previewResult, setPreviewResult] = useState<Record<string, unknown> | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  const { data: fields } = useRecordFields(recordTypeId || undefined);
  const activeFields = (fields || []).filter((f) => f.isActive);

  useEffect(() => {
    if (open) {
      if (editing) {
        setRecordTypeId(editing.recordTypeId);
        setMappingKey(editing.mappingKey);
        setName(editing.name);
        setDescription(editing.description || "");
        setMode(editing.mode);
        setIsActive(editing.isActive);
        setMappings(editing.configuration?.mappings ? [...editing.configuration.mappings] : []);
      } else {
        setRecordTypeId("");
        setMappingKey("");
        setName("");
        setDescription("");
        setMode("CUSTOM");
        setIsActive(true);
        setMappings([]);
      }
      setTouched(false);
    }
  }, [open, editing]);

  // When recordType changes, clear incompatible target fields
  useEffect(() => {
    if (!editing && recordTypeId) {
      // clear if not editing? For editing, we keep but filter on submit validation
    }
  }, [recordTypeId, editing]);

  function handleRecordTypeChange(val: string) {
    const newId = val;
    // Clear mappings whose target not in new type
    setRecordTypeId(newId);
    // We can't know new fields yet, but clear mappings that won't be valid
    // For now clear all mappings when type changes to avoid stale IDs
    if (mappings.length > 0) {
      // Keep mappings only if target still exists in new active fields? Need fields of new type – async
      // So clear to be safe and user re-adds
      setMappings([]);
    }
  }

  const keyError = !mappingKey.trim() ? "Mapping key is required" : !KEY_REGEX.test(mappingKey) ? "Lowercase a-z, 0-9, _ starting with letter" : null;
  const nameError = !name.trim() ? "Name is required" : null;
  const recordTypeError = !recordTypeId ? "Record Type is required" : null;

  // CUSTOM validation (includes WF-47 hardening: length 500, depth 10, transform compatibility)
  const seenSources = new Set<string>();
  const seenTargets = new Set<string>();
  let mappingsError: string | null = null;
  if (mode === "CUSTOM") {
    if (mappings.length === 0) mappingsError = "At least one mapping is required for CUSTOM mode";
    else if (mappings.length > 100) mappingsError = "Too many mappings (max 100)";
    else {
      for (const m of mappings) {
        if (!m.source?.trim()) { mappingsError = "Source path is required"; break; }
        if (!m.targetFieldId) { mappingsError = "Target field is required"; break; }
        const src = m.source.trim();
        if (src.length > 500) { mappingsError = "Source path too long (max 500)"; break; }
        const parts = src.split(".");
        if (parts.length > 10) { mappingsError = "Source path too deep (max 10)"; break; }
        if (parts.some((p) => !p)) { mappingsError = `Invalid source path '${src}' (empty segment)`; break; }
        if (seenSources.has(src)) { mappingsError = `Duplicate source '${src}'`; break; }
        seenSources.add(src);
        if (seenTargets.has(m.targetFieldId)) { mappingsError = "Duplicate target field"; break; }
        seenTargets.add(m.targetFieldId);
        if (src.startsWith(".") || src.endsWith(".") || src.includes("..")) { mappingsError = `Invalid source path '${src}'`; break; }
        if (m.transform && !["IDENTITY","STRING","INTEGER","DECIMAL","BOOLEAN","DATE","DATETIME","ENUM"].includes(m.transform)) {
          mappingsError = `Invalid transform '${m.transform}'`; break;
        }
      }
    }
    if (!mappingsError && recordTypeId) {
      for (const m of mappings) {
        const field = activeFields.find((f) => f.id === m.targetFieldId);
        if (!field) { mappingsError = "One or more target fields are invalid or inactive for selected Record Type"; break; }
        // transform compatibility (simple): only IDENTITY allowed for JSON
        if (field.fieldType === "JSON" && m.transform && m.transform !== "IDENTITY") {
          mappingsError = `Only IDENTITY allowed for JSON field '${field.fieldKey}'`; break;
        }
      }
    }
  }

  const canSave = !keyError && !nameError && !recordTypeError && !mappingsError;

  function addMapping() {
    setMappings((prev) => [...prev, { source: "", targetFieldId: "", transform: "IDENTITY" as MappingTransform }]);
  }
  function updateMapping(idx: number, patch: Partial<MappingEntry>) {
    setMappings((prev) => prev.map((m, i) => (i === idx ? { ...m, ...patch } : m)));
  }
  function removeMapping(idx: number) {
    setMappings((prev) => prev.filter((_, i) => i !== idx));
  }

  function handleSave() {
    setTouched(true);
    if (!canSave) return;
    onSubmit({
      recordTypeId,
      mappingKey: mappingKey.trim().toLowerCase(),
      name: name.trim(),
      description: description.trim() || undefined,
      mode,
      configuration: mode === "CUSTOM" ? { mappings: mappings.map((m) => ({ source: m.source.trim(), targetFieldId: m.targetFieldId, transform: m.transform && m.transform !== "IDENTITY" ? m.transform : undefined })) } : { mappings: [] },
      isActive,
    });
  }

  async function handlePreview() {
    if (!editing?.id) return;
    setPreviewError(null);
    setPreviewResult(null);
    let payload: Record<string, unknown>;
    try {
      payload = JSON.parse(sampleJson);
    } catch (e: any) {
      setPreviewError("Invalid JSON: " + e.message);
      return;
    }
    if (JSON.stringify(payload).length > 256 * 1024) {
      setPreviewError("Payload too large (>256KB)");
      return;
    }
    setPreviewLoading(true);
    try {
      const res = await recordMappingApi.preview(editing.id, payload);
      setPreviewResult(res.mappedData as Record<string, unknown>);
    } catch (e: any) {
      setPreviewError(e?.response?.data?.error?.message || e.message || "Preview failed");
    } finally {
      setPreviewLoading(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{editing ? "Edit Mapping Profile" : "New Mapping Profile"}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <Field>
            <FieldLabel>Record Type *</FieldLabel>
            <Select value={recordTypeId} onValueChange={handleRecordTypeChange} disabled={!!editing}>
              <SelectTrigger>
                <SelectValue placeholder="Select Record Type" />
              </SelectTrigger>
              <SelectContent>
                {types.map((t) => (
                  <SelectItem key={t.id} value={t.id}>
                    {t.name} ({t.key})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {touched && recordTypeError && <FieldError>{recordTypeError}</FieldError>}
            {editing && <p className="text-xs text-muted-foreground">Record Type cannot be changed after creation.</p>}
          </Field>
          <Field>
            <FieldLabel>Name *</FieldLabel>
            <Input
              value={name}
              onChange={(e) => {
                const v = e.target.value;
                setName(v);
                if (!editing) setMappingKey(slugify(v));
              }}
              placeholder="e.g. Sellspark CDR"
            />
            {touched && nameError && <FieldError>{nameError}</FieldError>}
          </Field>
          <Field>
            <FieldLabel>Mapping key *</FieldLabel>
            <Input value={mappingKey} onChange={(e) => setMappingKey(e.target.value.toLowerCase())} placeholder="e.g. sellspark_cdr" disabled={!!editing} className="font-mono text-sm" />
            <p className="text-xs text-muted-foreground">Stable tenant-scoped key for webhook routing. {editing ? "Immutable." : "Lowercase, numbers, underscores."}</p>
            {touched && keyError && <FieldError>{keyError}</FieldError>}
          </Field>
          <Field>
            <FieldLabel>Description</FieldLabel>
            <Textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Maps Sellspark dialer payload to CDR" rows={2} />
          </Field>
          <Field>
            <FieldLabel>Mode *</FieldLabel>
            <Select value={mode} onValueChange={(v) => setMode(v as MappingProfileMode)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="DIRECT">DIRECT – payload already uses canonical keys</SelectItem>
                <SelectItem value="CUSTOM">CUSTOM – explicit source → field mappings</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <div className="flex items-center justify-between rounded-lg border px-3 py-2">
            <div>
              <FieldLabel>Active</FieldLabel>
              <p className="text-xs text-muted-foreground">Inactive profiles are hidden from webhook selection.</p>
            </div>
            <Switch checked={isActive} onCheckedChange={setIsActive} />
          </div>

          {mode === "DIRECT" ? (
            <div className="rounded-lg bg-muted/30 border p-3 text-sm text-muted-foreground">
              <p className="font-medium text-foreground">DIRECT mode</p>
              <p>Incoming field names are expected to match the Record Type field keys exactly (e.g. <span className="font-mono">call_id</span>, <span className="font-mono">phone</span>). No transformation is applied; unknown fields will be rejected by record validation.</p>
            </div>
          ) : (
            <div className="space-y-3 border-t pt-4">
              <div className="flex items-center justify-between">
                <FieldLabel>Mappings *</FieldLabel>
                <Button type="button" variant="outline" size="sm" onClick={addMapping} disabled={!recordTypeId}>
                  <Plus className="h-3 w-3 mr-1" /> Add Mapping
                </Button>
              </div>
              {!recordTypeId && <p className="text-xs text-amber-600">Select a Record Type first.</p>}
              {recordTypeId && activeFields.length === 0 && <p className="text-xs text-amber-600">Selected Record Type has no active fields.</p>}
              {mappings.length === 0 ? (
                <p className="text-sm text-muted-foreground border border-dashed rounded-lg p-4 text-center">No mappings yet. Use Add Mapping to map external fields to canonical fields.</p>
              ) : (
                <div className="space-y-2">
                  <div className="grid grid-cols-[1.2fr_1fr_0.9fr_auto] gap-2 text-xs font-medium text-muted-foreground px-1">
                    <span>External Field / Path</span>
                    <span>CRM Field</span>
                    <span>Transform</span>
                    <span></span>
                  </div>
                  {mappings.map((m, idx) => (
                    <div key={idx} className="grid grid-cols-[1.2fr_1fr_0.9fr_auto] gap-2 items-center">
                      <Input value={m.source} onChange={(e) => updateMapping(idx, { source: e.target.value })} placeholder="e.g. callId or customer.phone" className="font-mono text-sm" />
                      <Select value={m.targetFieldId} onValueChange={(v) => updateMapping(idx, { targetFieldId: v })}>
                        <SelectTrigger>
                          <SelectValue placeholder="Select field" />
                        </SelectTrigger>
                        <SelectContent>
                          {activeFields.map((f) => (
                            <SelectItem key={f.id} value={f.id} disabled={mappings.some((mm, mi) => mi !== idx && mm.targetFieldId === f.id)}>
                              {f.fieldLabel} ({f.fieldKey})
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <Select value={m.transform || "IDENTITY"} onValueChange={(v) => updateMapping(idx, { transform: v as MappingTransform })}>
                        <SelectTrigger className="font-mono text-xs">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="IDENTITY">Identity</SelectItem>
                          <SelectItem value="STRING">String</SelectItem>
                          <SelectItem value="INTEGER">Integer</SelectItem>
                          <SelectItem value="DECIMAL">Decimal</SelectItem>
                          <SelectItem value="BOOLEAN">Boolean</SelectItem>
                          <SelectItem value="DATE">Date</SelectItem>
                          <SelectItem value="DATETIME">DateTime</SelectItem>
                          <SelectItem value="ENUM">Enum</SelectItem>
                        </SelectContent>
                      </Select>
                      <Button type="button" variant="ghost" size="icon" onClick={() => removeMapping(idx)}>
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  ))}
                </div>
              )}
              {touched && mappingsError && <FieldError>{mappingsError}</FieldError>}
              <p className="text-xs text-muted-foreground">Source supports dot-paths (max 10 depth, 500 chars). Transform normalizes e.g. string &quot;120&quot; → integer. Leave Identity if external type already matches RecordField.</p>
            </div>
          )}
          {mode === "CUSTOM" && editing && (
            <div className="space-y-2 border-t pt-4">
              <FieldLabel>Preview (sample payload → canonical)</FieldLabel>
              <p className="text-xs text-muted-foreground">Test mapping without creating a Record. Tenant-scoped, no persistence.</p>
              <Textarea value={sampleJson} onChange={(e) => setSampleJson(e.target.value)} rows={5} className="font-mono text-xs" placeholder='{"callId":"abc","mobile":"9876543210"}' />
              <div className="flex gap-2">
                <Button type="button" variant="outline" size="sm" onClick={handlePreview} disabled={previewLoading}>
                  <Eye className="h-3 w-3 mr-1" /> {previewLoading ? "Previewing..." : "Preview Mapping"}
                </Button>
              </div>
              {previewError && <p className="text-sm text-destructive">{previewError}</p>}
              {previewResult && (
                <pre className="text-xs bg-muted rounded p-2 overflow-auto max-h-40 font-mono">{JSON.stringify(previewResult, null, 2)}</pre>
              )}
            </div>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={isPending || !canSave}>
            {isPending ? "Saving..." : editing ? "Save Changes" : "Create"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
