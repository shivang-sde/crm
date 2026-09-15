"use client";

import { useState } from "react";
import { Plus, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FieldLabel } from "@/components/ui/field";

interface Props {
  options: string[];
  onChange: (options: string[]) => void;
  error?: string;
}

export function EnumOptionsEditor({ options, onChange, error }: Props) {
  const [draft, setDraft] = useState("");

  function add() {
    const v = draft.trim();
    if (!v) return;
    if (options.includes(v)) return;
    onChange([...options, v]);
    setDraft("");
  }

  function remove(idx: number) {
    onChange(options.filter((_, i) => i !== idx));
  }

  function update(idx: number, value: string) {
    const next = [...options];
    next[idx] = value;
    onChange(next);
  }

  return (
    <div className="space-y-2 border-t pt-4">
      <div className="flex items-center justify-between">
        <FieldLabel>Options *</FieldLabel>
        <span className="text-xs text-muted-foreground">{options.length} option(s)</span>
      </div>
      <p className="text-xs text-muted-foreground">Dropdown options for this field. Duplicates are not allowed.</p>
      <div className="space-y-2">
        {options.map((opt, i) => (
          <div key={i} className="flex gap-2 items-center">
            <Input
              value={opt}
              onChange={(e) => update(i, e.target.value)}
              placeholder={`Option ${i + 1}`}
              className="flex-1"
            />
            <Button type="button" variant="ghost" size="icon" onClick={() => remove(i)} aria-label="Remove option">
              <X className="h-4 w-4" />
            </Button>
          </div>
        ))}
      </div>
      <div className="flex gap-2">
        <Input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="New option (e.g. ANSWERED)"
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              add();
            }
          }}
          className="flex-1"
        />
        <Button type="button" variant="outline" onClick={add} disabled={!draft.trim()}>
          <Plus className="h-4 w-4 mr-1" /> Add
        </Button>
      </div>
      {error && <p className="text-sm text-destructive">{error}</p>}
      {options.length === 0 && <p className="text-sm text-amber-600">At least one option is required for Dropdown fields.</p>}
      {new Set(options).size !== options.length && (
        <p className="text-sm text-destructive">Duplicate options are not allowed.</p>
      )}
      {options.some((o) => !o.trim()) && <p className="text-sm text-destructive">Options cannot be blank.</p>}
    </div>
  );
}
