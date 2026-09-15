"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Field, FieldLabel, FieldError } from "@/components/ui/field";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { RecordTypeResponse } from "@/types/records";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editing: RecordTypeResponse | null;
  onSubmit: (data: { key: string; name: string; description?: string; isActive: boolean }) => void;
  isPending: boolean;
}

function slugify(label: string) {
  return label.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "").replace(/__+/g, "_");
}

const KEY_REGEX = /^[a-z][a-z0-9_]*$/;

export function RecordTypeDialog({ open, onOpenChange, editing, onSubmit, isPending }: Props) {
  const [name, setName] = useState("");
  const [key, setKey] = useState("");
  const [description, setDescription] = useState("");
  const [isActive, setIsActive] = useState(true);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (open) {
      if (editing) {
        setName(editing.name);
        setKey(editing.key);
        setDescription(editing.description || "");
        setIsActive(editing.isActive);
      } else {
        setName("");
        setKey("");
        setDescription("");
        setIsActive(true);
      }
      setTouched(false);
    }
  }, [open, editing]);

  const keyError = !key.trim() ? "Key is required" : !KEY_REGEX.test(key) ? "Key must be lowercase (a-z, 0-9, _) starting with a letter" : null;
  const nameError = !name.trim() ? "Name is required" : null;
  const canSave = !keyError && !nameError;

  function handleSave() {
    setTouched(true);
    if (!canSave) return;
    onSubmit({ key: key.trim(), name: name.trim(), description: description.trim() || undefined, isActive });
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{editing ? "Edit Record Type" : "New Record Type"}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <Field>
            <FieldLabel>Name *</FieldLabel>
            <Input
              value={name}
              onChange={(e) => {
                const v = e.target.value;
                setName(v);
                if (!editing) setKey(slugify(v));
              }}
              placeholder="e.g. CDR"
            />
            {touched && nameError && <FieldError>{nameError}</FieldError>}
          </Field>
          <Field>
            <FieldLabel>Key *</FieldLabel>
            <Input
              value={key}
              onChange={(e) => setKey(e.target.value.toLowerCase())}
              placeholder="e.g. cdr"
              disabled={!!editing}
              className="font-mono text-sm"
            />
            <p className="text-xs text-muted-foreground">
              Stable programmatic identity used in APIs & workflows. {editing ? "Key cannot be changed after creation." : "Lowercase, numbers, underscores only."}
            </p>
            {touched && keyError && <FieldError>{keyError}</FieldError>}
          </Field>
          <Field>
            <FieldLabel>Description</FieldLabel>
            <Textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Call detail records" rows={3} />
          </Field>
          <div className="flex items-center justify-between rounded-lg border px-3 py-2">
            <div>
              <FieldLabel>Active</FieldLabel>
              <p className="text-xs text-muted-foreground">Inactive types are hidden from new records.</p>
            </div>
            <Switch checked={isActive} onCheckedChange={setIsActive} />
          </div>
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
