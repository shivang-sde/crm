"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Field, FieldLabel, FieldError } from "@/components/ui/field";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { RecordFieldResponse, RecordFieldType, RECORD_FIELD_TYPE_LABELS } from "@/types/records";
import { EnumOptionsEditor } from "./EnumOptionsEditor";

const ALL_TYPES: RecordFieldType[] = [
  "TEXT",
  "LONG_TEXT",
  "INTEGER",
  "DECIMAL",
  "BOOLEAN",
  "DATE",
  "DATETIME",
  "ENUM",
  "URL",
  "PHONE",
  "EMAIL",
  "JSON",
  "REFERENCE",
];

const REFERENCE_TARGETS = ["LEAD", "CONTACT", "ACCOUNT", "DEAL", "TASK", "MEETING", "CALL"] as const;

function slugify(label: string) {
  return label.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "").replace(/__+/g, "_");
}
const KEY_REGEX = /^[a-z][a-z0-9_]*$/;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editing: RecordFieldResponse | null;
  existingKeys: string[];
  nextOrder: number;
  onSubmit: (data: {
    fieldKey: string;
    fieldLabel: string;
    fieldType: RecordFieldType;
    isRequired: boolean;
    isActive: boolean;
    displayOrder: number;
    options?: string[];
    defaultValue?: string;
    referenceEntityType?: string;
  }) => void;
  isPending: boolean;
}

export function RecordFieldDialog({ open, onOpenChange, editing, existingKeys, nextOrder, onSubmit, isPending }: Props) {
  const [fieldLabel, setFieldLabel] = useState("");
  const [fieldKey, setFieldKey] = useState("");
  const [fieldType, setFieldType] = useState<RecordFieldType>("TEXT");
  const [isRequired, setIsRequired] = useState(false);
  const [isActive, setIsActive] = useState(true);
  const [displayOrder, setDisplayOrder] = useState(0);
  const [options, setOptions] = useState<string[]>([]);
  const [defaultValue, setDefaultValue] = useState("");
  const [referenceEntityType, setReferenceEntityType] = useState<string>("LEAD");
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (open) {
      if (editing) {
        setFieldLabel(editing.fieldLabel);
        setFieldKey(editing.fieldKey);
        setFieldType(editing.fieldType);
        setIsRequired(editing.isRequired);
        setIsActive(editing.isActive);
        setDisplayOrder(editing.displayOrder);
        setOptions(editing.options ? [...editing.options] : []);
        setDefaultValue(editing.defaultValue || "");
        setReferenceEntityType(editing.referenceEntityType || "LEAD");
      } else {
        setFieldLabel("");
        setFieldKey("");
        setFieldType("TEXT");
        setIsRequired(false);
        setIsActive(true);
        setDisplayOrder(nextOrder);
        setOptions([]);
        setDefaultValue("");
        setReferenceEntityType("LEAD");
      }
      setTouched(false);
    }
  }, [open, editing, nextOrder]);

  const isEnum = fieldType === "ENUM";
  const isReference = fieldType === "REFERENCE";
  const keyError = !fieldKey.trim()
    ? "Field key is required"
    : !KEY_REGEX.test(fieldKey)
      ? "Lowercase a-z, 0-9, _ starting with letter"
      : !editing && existingKeys.includes(fieldKey)
        ? "Field key already exists for this record type"
        : null;
  const labelError = !fieldLabel.trim() ? "Label is required" : null;
  const enumError = isEnum && options.length === 0 ? "At least one option required" : isEnum && new Set(options).size !== options.length ? "Duplicate options not allowed" : isEnum && options.some((o) => !o.trim()) ? "Options cannot be blank" : null;
  const defaultEnumError = isEnum && defaultValue && !options.includes(defaultValue) ? "Default must be one of the options" : null;
  const referenceError = isReference && !referenceEntityType ? "Reference target is required" : null;
  const canSave = !keyError && !labelError && !enumError && !defaultEnumError && !referenceError;

  function handleSave() {
    setTouched(true);
    if (!canSave) return;
    onSubmit({
      fieldKey: fieldKey.trim(),
      fieldLabel: fieldLabel.trim(),
      fieldType,
      isRequired,
      isActive,
      displayOrder,
      options: isEnum ? options.map((o) => o.trim()) : undefined,
      defaultValue: defaultValue.trim() || undefined,
      referenceEntityType: isReference ? referenceEntityType : undefined,
    });
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{editing ? "Edit Field" : "New Field"}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <Field>
            <FieldLabel>Label *</FieldLabel>
            <Input
              value={fieldLabel}
              onChange={(e) => {
                const v = e.target.value;
                setFieldLabel(v);
                if (!editing) setFieldKey(slugify(v));
              }}
              placeholder="e.g. Call ID"
            />
            {touched && labelError && <FieldError>{labelError}</FieldError>}
          </Field>
          <Field>
            <FieldLabel>Field key *</FieldLabel>
            <Input
              value={fieldKey}
              onChange={(e) => setFieldKey(e.target.value.toLowerCase())}
              placeholder="e.g. call_id"
              disabled={!!editing}
              className="font-mono text-sm"
            />
            <p className="text-xs text-muted-foreground">Stable identity used in APIs & workflows. Cannot be changed after creation.</p>
            {touched && keyError && <FieldError>{keyError}</FieldError>}
          </Field>
          <Field>
            <FieldLabel>Type *</FieldLabel>
            <Select
              value={fieldType}
              onValueChange={(v) => {
                const nt = v as RecordFieldType;
                setFieldType(nt);
                if (nt === "ENUM" && options.length === 0) setOptions([""]);
                if (nt !== "ENUM") setOptions([]);
              }}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ALL_TYPES.map((t) => (
                  <SelectItem key={t} value={t}>
                    {RECORD_FIELD_TYPE_LABELS[t]} ({t})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          {isReference && (
            <Field>
              <FieldLabel>Reference target *</FieldLabel>
              <Select value={referenceEntityType} onValueChange={setReferenceEntityType}>
                <SelectTrigger>
                  <SelectValue placeholder="Select CRM entity" />
                </SelectTrigger>
                <SelectContent>
                  {REFERENCE_TARGETS.map((t) => (
                    <SelectItem key={t} value={t}>
                      {t}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">One-hop tenant-safe link. Value must be a UUID of the selected CRM entity. Exposed as entity.{referenceEntityType.toLowerCase()}.* in workflows.</p>
              {touched && referenceError && <FieldError>{referenceError}</FieldError>}
            </Field>
          )}
          <Field>
            <FieldLabel>Display order</FieldLabel>
            <Input type="number" value={displayOrder} onChange={(e) => setDisplayOrder(Number(e.target.value))} />
            <p className="text-xs text-muted-foreground">Lower numbers appear first.</p>
          </Field>
          <div className="flex items-center justify-between rounded-lg border px-3 py-2">
            <div>
              <FieldLabel>Required</FieldLabel>
              <p className="text-xs text-muted-foreground">New records must provide this field.</p>
            </div>
            <Switch checked={isRequired} onCheckedChange={setIsRequired} />
          </div>
          <div className="flex items-center justify-between rounded-lg border px-3 py-2">
            <div>
              <FieldLabel>Active</FieldLabel>
              <p className="text-xs text-muted-foreground">Inactive fields are hidden from new records but history is preserved.</p>
            </div>
            <Switch checked={isActive} onCheckedChange={setIsActive} />
          </div>

          {isEnum && <EnumOptionsEditor options={options} onChange={setOptions} error={touched ? enumError || undefined : undefined} />}

          <Field>
            <FieldLabel>Default value</FieldLabel>
            {fieldType === "BOOLEAN" ? (
              <Select value={defaultValue || "none"} onValueChange={(v) => setDefaultValue(v === "none" ? "" : v)}>
                <SelectTrigger>
                  <SelectValue placeholder="No default" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">No default</SelectItem>
                  <SelectItem value="true">true</SelectItem>
                  <SelectItem value="false">false</SelectItem>
                </SelectContent>
              </Select>
            ) : isEnum ? (
              <Select value={defaultValue || "none"} onValueChange={(v) => setDefaultValue(v === "none" ? "" : v)}>
                <SelectTrigger>
                  <SelectValue placeholder="No default" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">No default</SelectItem>
                  {options.map((o) => (
                    <SelectItem key={o} value={o}>
                      {o}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : (
              <Input value={defaultValue} onChange={(e) => setDefaultValue(e.target.value)} placeholder="Optional default" />
            )}
            {touched && defaultEnumError && <FieldError>{defaultEnumError}</FieldError>}
            <p className="text-xs text-muted-foreground">
              {isEnum ? "Must be one of the options." : fieldType === "INTEGER" ? "Must be an integer." : fieldType === "DECIMAL" ? "Must be a number." : "Optional."}
            </p>
          </Field>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={isPending || !canSave}>
            {isPending ? "Saving..." : editing ? "Save Changes" : "Create Field"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
