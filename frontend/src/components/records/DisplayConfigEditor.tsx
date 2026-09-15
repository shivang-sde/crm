"use client";

import { useEffect, useState } from "react";
import { Plus, Trash2, ChevronUp, ChevronDown, Eye, EyeOff, RotateCcw, Save, Loader2, GripVertical, AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { useDisplayConfig, usePutDisplayConfig, useResetDisplayConfig, useRecordFields } from "@/lib/hooks/records";
import { DisplayConfigRequest, RecordFieldResponse, RECORD_FIELD_TYPE_LABELS } from "@/types/records";

interface Props {
  recordTypeId: string;
}

function move<T>(arr: T[], from: number, to: number): T[] {
  const next = [...arr];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

export function DisplayConfigEditor({ recordTypeId }: Props) {
  const { data: fields, isLoading: fieldsLoading } = useRecordFields(recordTypeId);
  const { data: display, isLoading: displayLoading, isError, error } = useDisplayConfig(recordTypeId);
  const putMutation = usePutDisplayConfig();
  const resetMutation = useResetDisplayConfig();

  const [visibleIds, setVisibleIds] = useState<string[]>([]);
  const [sections, setSections] = useState<{ id: string; name: string; fieldIds: string[] }[]>([]);
  const [dirty, setDirty] = useState(false);
  const [sectionDraft, setSectionDraft] = useState("");

  const activeFields = (fields || []).filter((f) => f.isActive);
  const allFields = fields || [];
  const fieldById = new Map<string, RecordFieldResponse>(allFields.map((f) => [f.id, f]));

  // Initialize from server
  useEffect(() => {
    if (display && fields) {
      const listIds = (display.list?.columns || []).map((c) => c.fieldId).filter((id) => fieldById.has(id));
      // If custom but empty list, keep empty; if default, listIds will be all active.
      setVisibleIds(listIds);
      const detailSections = (display.detail?.sections || []).map((s) => ({
        id: s.id,
        name: s.name,
        fieldIds: (s.fieldIds || []).filter((fid) => fieldById.has(fid)),
      }));
      setSections(detailSections);
      setDirty(false);
    }
  }, [display, fields]);

  if (fieldsLoading || displayLoading) {
    return (
      <div className="flex justify-center py-12">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }
  if (isError) {
    return <div className="rounded-lg border border-destructive/20 bg-destructive/5 p-4 text-sm text-destructive">Failed to load display config: {(error as any)?.response?.data?.error?.message || (error as Error).message}</div>;
  }
  if (!fields) return <div className="text-sm text-muted-foreground">No fields found.</div>;

  const availableIds = activeFields.map((f) => f.id).filter((id) => !visibleIds.includes(id));
  const allVisibleAndAvailable = [...visibleIds, ...availableIds];
  // For detail, compute unassigned active field ids (fields not in any section)
  const assignedDetailIds = new Set(sections.flatMap((s) => s.fieldIds));
  const unassignedIds = activeFields.map((f) => f.id).filter((id) => !assignedDetailIds.has(id));

  function toggleVisible(fieldId: string) {
    setVisibleIds((prev) => (prev.includes(fieldId) ? prev.filter((id) => id !== fieldId) : [...prev, fieldId]));
    setDirty(true);
  }
  function moveVisible(from: number, to: number) {
    setVisibleIds((prev) => move(prev, from, to));
    setDirty(true);
  }
  function handleSave() {
    // Validate sections have names
    for (const s of sections) {
      if (!s.name.trim()) {
        alert("Section name is required");
        return;
      }
    }
    // Check duplicate fieldIds across detail sections already prevented by UI
    const payload: DisplayConfigRequest = {
      list: { columns: visibleIds.map((fieldId) => ({ fieldId })) },
      detail: { sections: sections.map((s) => ({ id: s.id, name: s.name.trim(), fieldIds: [...s.fieldIds] })) },
    };
    putMutation.mutate({ recordTypeId, data: payload }, { onSuccess: () => setDirty(false) });
  }
  function handleReset() {
    resetMutation.mutate(recordTypeId, {
      onSuccess: () => setDirty(false),
    });
  }
  function addSection() {
    const name = sectionDraft.trim() || `Section ${sections.length + 1}`;
    setSections((prev) => [...prev, { id: `sec_${Date.now()}`, name, fieldIds: [] }]);
    setSectionDraft("");
    setDirty(true);
  }
  function removeSection(idx: number) {
    setSections((prev) => prev.filter((_, i) => i !== idx));
    setDirty(true);
  }
  function moveSection(from: number, to: number) {
    setSections((prev) => move(prev, from, to));
    setDirty(true);
  }
  function renameSection(idx: number, name: string) {
    setSections((prev) => prev.map((s, i) => (i === idx ? { ...s, name } : s)));
    setDirty(true);
  }
  function addFieldToSection(sectionIdx: number, fieldId: string) {
    if (!fieldId) return;
    // enforce one field = one detail location
    if (assignedDetailIds.has(fieldId)) return;
    setSections((prev) => prev.map((s, i) => (i === sectionIdx ? { ...s, fieldIds: [...s.fieldIds, fieldId] } : s)));
    setDirty(true);
  }
  function removeFieldFromSection(sectionIdx: number, fieldId: string) {
    setSections((prev) => prev.map((s, i) => (i === sectionIdx ? { ...s, fieldIds: s.fieldIds.filter((id) => id !== fieldId) } : s)));
    setDirty(true);
  }
  function moveFieldInSection(sectionIdx: number, from: number, to: number) {
    setSections((prev) => prev.map((s, i) => (i === sectionIdx ? { ...s, fieldIds: move(s.fieldIds, from, to) } : s)));
    setDirty(true);
  }

  const isSaving = putMutation.isPending || resetMutation.isPending;
  const isCustom = display?.isCustom;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h3 className="font-semibold">Display Configuration</h3>
          <p className="text-sm text-muted-foreground">
            {isCustom ? "Custom layout saved. Changes affect future list & detail views." : "No custom layout yet – default shows all active fields ordered by field order."}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={handleReset} disabled={isSaving || !isCustom}>
            <RotateCcw className="h-4 w-4 mr-1" /> Reset to Default
          </Button>
          <Button size="sm" onClick={handleSave} disabled={isSaving || !dirty}>
            {isSaving ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : <Save className="h-4 w-4 mr-1" />} Save
          </Button>
        </div>
      </div>

      {/* List View */}
      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Eye className="h-5 w-5" /> List View
            <Badge variant="secondary" className="ml-2">
              {visibleIds.length} visible
            </Badge>
          </CardTitle>
          <CardDescription>Choose which fields appear as columns in the record list. Drag to reorder visible columns.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {activeFields.length === 0 ? (
            <p className="text-sm text-muted-foreground">No active fields to display.</p>
          ) : (
            <>
              <div className="rounded-lg border divide-y bg-white">
                <div className="px-3 py-2 text-xs font-medium text-muted-foreground bg-muted/30">Visible Columns (drag to reorder)</div>
                {visibleIds.length === 0 ? (
                  <div className="px-3 py-8 text-center text-sm text-muted-foreground">No columns selected. List will be empty until you add fields.</div>
                ) : (
                  visibleIds.map((fid, idx) => {
                    const f = fieldById.get(fid);
                    if (!f) return null;
                    const isInactive = !f.isActive;
                    return (
                      <div key={fid} className="flex items-center gap-2 px-3 py-2 hover:bg-muted/20">
                        <GripVertical className="h-4 w-4 text-muted-foreground/40" />
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium truncate flex items-center gap-1">
                            {f.fieldLabel} {isInactive && <AlertTriangle className="h-3 w-3 text-amber-500" />}
                          </p>
                          <p className="text-xs font-mono text-muted-foreground truncate">{f.fieldKey} · {RECORD_FIELD_TYPE_LABELS[f.fieldType]}</p>
                        </div>
                        <Badge variant={isInactive ? "outline" : "secondary"} className={isInactive ? "border-amber-200 text-amber-700" : ""}>
                          {isInactive ? "Inactive" : RECORD_FIELD_TYPE_LABELS[f.fieldType]}
                        </Badge>
                        <div className="flex gap-1">
                          <Button variant="ghost" size="icon" className="h-7 w-7" disabled={idx === 0} onClick={() => moveVisible(idx, idx - 1)}>
                            <ChevronUp className="h-4 w-4" />
                          </Button>
                          <Button variant="ghost" size="icon" className="h-7 w-7" disabled={idx === visibleIds.length - 1} onClick={() => moveVisible(idx, idx + 1)}>
                            <ChevronDown className="h-4 w-4" />
                          </Button>
                          <Button variant="ghost" size="icon" className="h-7 w-7 text-amber-600" onClick={() => toggleVisible(fid)} title="Hide">
                            <EyeOff className="h-4 w-4" />
                          </Button>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>

              <div className="rounded-lg border divide-y bg-white">
                <div className="px-3 py-2 text-xs font-medium text-muted-foreground bg-muted/30">Available Fields (hidden)</div>
                {availableIds.length === 0 ? (
                  <div className="px-3 py-4 text-center text-xs text-muted-foreground">All active fields are visible.</div>
                ) : (
                  availableIds.map((fid) => {
                    const f = fieldById.get(fid)!;
                    return (
                      <div key={fid} className="flex items-center gap-2 px-3 py-2 hover:bg-muted/20">
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium truncate">{f.fieldLabel}</p>
                          <p className="text-xs font-mono text-muted-foreground truncate">{f.fieldKey} · {RECORD_FIELD_TYPE_LABELS[f.fieldType]}</p>
                        </div>
                        <Button variant="outline" size="sm" className="h-7" onClick={() => toggleVisible(fid)}>
                          <Eye className="h-3 w-3 mr-1" /> Show
                        </Button>
                      </div>
                    );
                  })
                )}
              </div>

              {/* Inactive fields warning */}
              {allFields.some((f) => !f.isActive && visibleIds.includes(f.id)) && (
                <div className="flex gap-2 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg p-2">
                  <AlertTriangle className="h-4 w-4 shrink-0" /> Some visible columns reference inactive fields. They will be hidden in the actual list until re-activated.
                </div>
              )}
            </>
          )}
          <div className="pt-2 flex justify-end">
            <Button size="sm" onClick={handleSave} disabled={!dirty || isSaving}>
              Save List Layout
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Detail View */}
      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle className="text-base">Detail View</CardTitle>
          <CardDescription>Organize fields into sections for the record detail page. One field can appear in only one section.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {sections.length === 0 && <p className="text-sm text-muted-foreground">No sections yet. Add a section to group fields.</p>}
          {sections.map((sec, secIdx) => (
            <div key={sec.id} className="rounded-xl border bg-white shadow-sm overflow-hidden">
              <div className="flex items-center gap-2 px-3 py-2 bg-muted/30 border-b">
                <GripVertical className="h-4 w-4 text-muted-foreground/40" />
                <Input value={sec.name} onChange={(e) => renameSection(secIdx, e.target.value)} placeholder="Section name" className="h-8 font-medium flex-1" />
                <div className="flex gap-1">
                  <Button variant="ghost" size="icon" className="h-7 w-7" disabled={secIdx === 0} onClick={() => moveSection(secIdx, secIdx - 1)}>
                    <ChevronUp className="h-4 w-4" />
                  </Button>
                  <Button variant="ghost" size="icon" className="h-7 w-7" disabled={secIdx === sections.length - 1} onClick={() => moveSection(secIdx, secIdx + 1)}>
                    <ChevronDown className="h-4 w-4" />
                  </Button>
                  <Button variant="ghost" size="icon" className="h-7 w-7 text-destructive" onClick={() => removeSection(secIdx)}>
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </div>
              <div className="divide-y">
                {sec.fieldIds.length === 0 ? (
                  <div className="px-3 py-6 text-center text-xs text-muted-foreground">No fields in this section.</div>
                ) : (
                  sec.fieldIds.map((fid, fIdx) => {
                    const f = fieldById.get(fid);
                    if (!f) return (
                      <div key={fid} className="flex items-center gap-2 px-3 py-2 bg-amber-50 text-amber-800 text-xs">
                        <AlertTriangle className="h-3 w-3" /> Unknown/inactive field {fid.slice(0, 8)}
                        <Button variant="ghost" size="sm" className="ml-auto h-6 text-destructive" onClick={() => removeFieldFromSection(secIdx, fid)}>
                          Remove
                        </Button>
                      </div>
                    );
                    const isInactive = !f.isActive;
                    return (
                      <div key={fid} className="flex items-center gap-2 px-3 py-2 hover:bg-muted/20">
                        <GripVertical className="h-4 w-4 text-muted-foreground/30" />
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium truncate">{f.fieldLabel}</p>
                          <p className="text-xs font-mono text-muted-foreground truncate">{f.fieldKey}</p>
                        </div>
                        {isInactive && <Badge variant="outline" className="border-amber-200 text-amber-700">Inactive</Badge>}
                        <Badge variant="outline" className="hidden sm:inline-flex">
                          {RECORD_FIELD_TYPE_LABELS[f.fieldType]}
                        </Badge>
                        <div className="flex gap-1">
                          <Button variant="ghost" size="icon" className="h-7 w-7" disabled={fIdx === 0} onClick={() => moveFieldInSection(secIdx, fIdx, fIdx - 1)}>
                            <ChevronUp className="h-4 w-4" />
                          </Button>
                          <Button variant="ghost" size="icon" className="h-7 w-7" disabled={fIdx === sec.fieldIds.length - 1} onClick={() => moveFieldInSection(secIdx, fIdx, fIdx + 1)}>
                            <ChevronDown className="h-4 w-4" />
                          </Button>
                          <Button variant="ghost" size="icon" className="h-7 w-7 text-destructive" onClick={() => removeFieldFromSection(secIdx, fid)}>
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
              <div className="px-3 py-2 bg-muted/20 flex gap-2">
                <Select
                  onValueChange={(v) => addFieldToSection(secIdx, v)}
                  value=""
                >
                  <SelectTrigger className="h-8 flex-1">
                    <SelectValue placeholder={unassignedIds.length === 0 ? "All fields assigned" : "Add field…"} />
                  </SelectTrigger>
                  <SelectContent>
                    {unassignedIds.map((id) => {
                      const f = fieldById.get(id)!;
                      return (
                        <SelectItem key={id} value={id}>
                          {f.fieldLabel} ({f.fieldKey})
                        </SelectItem>
                      );
                    })}
                  </SelectContent>
                </Select>
              </div>
            </div>
          ))}

          <div className="flex gap-2">
            <Input value={sectionDraft} onChange={(e) => setSectionDraft(e.target.value)} placeholder="New section name (e.g. Call Information)" className="flex-1" />
            <Button variant="outline" onClick={addSection}>
              <Plus className="h-4 w-4 mr-1" /> Add Section
            </Button>
          </div>

          <Separator />
          <div className="flex justify-between items-center">
            <p className="text-xs text-muted-foreground">{unassignedIds.length} unassigned active field{unassignedIds.length !== 1 ? "s" : ""}</p>
            <Button size="sm" onClick={handleSave} disabled={!dirty || isSaving}>
              Save Detail Layout
            </Button>
          </div>

          {/* Preview */}
          <div className="rounded-lg border bg-muted/20 p-3 space-y-2">
            <p className="text-xs font-medium text-muted-foreground">Live Preview</p>
            <div className="bg-white rounded-lg border p-3 space-y-3">
              <div className="flex gap-2 text-[11px] font-medium text-muted-foreground border-b pb-2">
                {visibleIds.slice(0, 5).map((fid) => {
                  const f = fieldById.get(fid);
                  return <span key={fid} className="truncate">{f?.fieldLabel || fid.slice(0, 6)}</span>;
                })}
                {visibleIds.length === 0 && <span className="text-muted-foreground/60">No list columns</span>}
                {visibleIds.length > 5 && <span className="text-muted-foreground">+{visibleIds.length - 5} more</span>}
              </div>
              {sections.map((sec) => (
                <div key={sec.id} className="space-y-1">
                  <p className="text-xs font-semibold text-indigo-700">{sec.name}</p>
                  <div className="flex flex-wrap gap-1">
                    {sec.fieldIds.map((fid) => {
                      const f = fieldById.get(fid);
                      return (
                        <Badge key={fid} variant="secondary" className="text-[11px]">
                          {f?.fieldLabel || fid.slice(0, 6)}
                        </Badge>
                      );
                    })}
                    {sec.fieldIds.length === 0 && <span className="text-xs text-muted-foreground">— empty —</span>}
                  </div>
                </div>
              ))}
              {sections.length === 0 && <p className="text-xs text-muted-foreground">No detail sections</p>}
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={handleReset} disabled={isSaving || !isCustom}>
          Reset to Default
        </Button>
        <Button onClick={handleSave} disabled={!dirty || isSaving}>
          {isSaving ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : <Save className="h-4 w-4 mr-1" />} Save All
        </Button>
      </div>
    </div>
  );
}
