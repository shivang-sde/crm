"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Field, FieldLabel, FieldError } from "@/components/ui/field";
import { RecordFieldResponse } from "@/types/records";
import { Loader2 } from "lucide-react";
import { ReferenceEntitySelector } from "./ReferenceEntitySelector";

interface Props {
  fields: RecordFieldResponse[];
  initialData?: Record<string, unknown>;
  onSubmit: (data: Record<string, unknown>) => void;
  isPending?: boolean;
  submitLabel?: string;
}

export function RecordForm({ fields, initialData, onSubmit, isPending, submitLabel = "Save" }: Props) {
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});

  const activeFields = fields.filter((f) => f.isActive);

  useEffect(() => {
    const init: Record<string, unknown> = {};
    for (const f of activeFields) {
      if (initialData && initialData[f.fieldKey] !== undefined) {
        init[f.fieldKey] = initialData[f.fieldKey];
      } else if (f.defaultValue !== null && f.defaultValue !== undefined && f.defaultValue !== "") {
        // coerce defaultValue string to type
        if (f.fieldType === "BOOLEAN") {
          init[f.fieldKey] = f.defaultValue === "true";
        } else if (f.fieldType === "INTEGER") {
          const n = Number(f.defaultValue);
          init[f.fieldKey] = isNaN(n) ? f.defaultValue : Math.trunc(n);
        } else if (f.fieldType === "DECIMAL") {
          const n = Number(f.defaultValue);
          init[f.fieldKey] = isNaN(n) ? f.defaultValue : n;
        } else if (f.fieldType === "JSON") {
          try {
            init[f.fieldKey] = JSON.parse(f.defaultValue);
          } catch {
            init[f.fieldKey] = f.defaultValue;
          }
        } else {
          init[f.fieldKey] = f.defaultValue;
        }
      } else {
        // leave undefined
      }
    }
    setValues(init);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fields, initialData]);

  function setValue(key: string, value: unknown) {
    setValues((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      const next = { ...prev };
      delete next[key];
      return next;
    });
  }

  function validate(): boolean {
    const nextErrors: Record<string, string> = {};
    for (const f of activeFields) {
      const v = values[f.fieldKey];
      if (f.isRequired && (v === undefined || v === null || v === "" || (Array.isArray(v) && v.length === 0))) {
        nextErrors[f.fieldKey] = "Required";
        continue;
      }
      if (v === undefined || v === null || v === "") continue;
      switch (f.fieldType) {
        case "INTEGER":
          if (typeof v === "string" && v.trim() !== "" && isNaN(Number(v))) nextErrors[f.fieldKey] = "Must be an integer";
          else if (typeof v === "number" && !Number.isInteger(Number(v))) nextErrors[f.fieldKey] = "Must be an integer";
          break;
        case "DECIMAL":
          if (v !== "" && isNaN(Number(v as any))) nextErrors[f.fieldKey] = "Must be a number";
          break;
        case "EMAIL":
          if (typeof v === "string" && !v.includes("@")) nextErrors[f.fieldKey] = "Invalid email";
          break;
        case "URL":
          if (typeof v === "string") {
            try {
              const u = new URL(v);
              if (!u.protocol.startsWith("http")) throw new Error();
            } catch {
              nextErrors[f.fieldKey] = "Invalid URL (http/https only)";
            }
          }
          break;
        case "DATE":
          if (typeof v === "string" && isNaN(Date.parse(v))) nextErrors[f.fieldKey] = "Invalid date";
          break;
        case "DATETIME":
          if (typeof v === "string" && isNaN(Date.parse(v))) nextErrors[f.fieldKey] = "Invalid datetime";
          break;
        case "ENUM":
          if (f.options && !f.options.includes(String(v))) nextErrors[f.fieldKey] = "Invalid option";
          break;
        case "JSON":
          if (typeof v === "string" && v.trim() !== "") {
            try {
              JSON.parse(v as string);
            } catch {
              nextErrors[f.fieldKey] = "Invalid JSON";
            }
          }
          break;
        case "REFERENCE": {
          if (typeof v === "string" && v.trim() !== "") {
            const trimmed = v.trim();
            if (!/^[0-9a-fA-F-]{36}$/.test(trimmed)) nextErrors[f.fieldKey] = `Must be a valid UUID for ${f.referenceEntityType || "reference"}`;
          }
          break;
        }
        default:
          break;
      }
    }
    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!validate()) return;
    // Normalize values: coerce numbers, booleans, JSON
    const normalized: Record<string, unknown> = {};
    for (const f of activeFields) {
      let v = values[f.fieldKey];
      if (v === "" || v === undefined) continue;
      switch (f.fieldType) {
        case "INTEGER":
          if (typeof v === "string") {
            const n = Number(v);
            v = isNaN(n) ? v : Math.trunc(n);
          }
          break;
        case "DECIMAL":
          if (typeof v === "string") {
            const n = Number(v);
            v = isNaN(n) ? v : n;
          }
          break;
        case "BOOLEAN":
          if (typeof v === "string") v = v === "true";
          break;
        case "JSON":
          if (typeof v === "string" && v.trim() !== "") {
            try {
              v = JSON.parse(v as string);
            } catch {
              // keep string, validation already flagged
            }
          }
          break;
        default:
          break;
      }
      if (v !== undefined && v !== "") normalized[f.fieldKey] = v;
    }
    onSubmit(normalized);
  }

  if (activeFields.length === 0) {
    return <p className="text-sm text-muted-foreground">This Record Type has no active fields.</p>;
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {activeFields
        .sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0))
        .map((field) => {
          const val = values[field.fieldKey];
          const err = errors[field.fieldKey];
          return (
            <Field key={field.id}>
              <FieldLabel>
                {field.fieldLabel} {field.isRequired && <span className="text-destructive">*</span>}
              </FieldLabel>
              {field.fieldType === "TEXT" && <Input value={(val as string) ?? ""} onChange={(e) => setValue(field.fieldKey, e.target.value)} placeholder={field.fieldLabel} />}
              {field.fieldType === "LONG_TEXT" && <Textarea value={(val as string) ?? ""} onChange={(e) => setValue(field.fieldKey, e.target.value)} placeholder={field.fieldLabel} rows={3} />}
              {field.fieldType === "INTEGER" && <Input type="number" step="1" value={(val as any) ?? ""} onChange={(e) => setValue(field.fieldKey, e.target.value)} placeholder="0" />}
              {field.fieldType === "DECIMAL" && <Input type="number" step="any" value={(val as any) ?? ""} onChange={(e) => setValue(field.fieldKey, e.target.value)} placeholder="0.00" />}
              {field.fieldType === "BOOLEAN" && (
                <div className="flex items-center gap-2 pt-1">
                  <Switch checked={Boolean(val)} onCheckedChange={(v) => setValue(field.fieldKey, v)} />
                  <span className="text-sm text-muted-foreground">{Boolean(val) ? "Yes" : "No"}</span>
                </div>
              )}
              {field.fieldType === "DATE" && <Input type="date" value={(val as string) ?? ""} onChange={(e) => setValue(field.fieldKey, e.target.value)} />}
              {field.fieldType === "DATETIME" && <Input type="datetime-local" value={(val as string) ?? ""} onChange={(e) => setValue(field.fieldKey, e.target.value)} />}
              {field.fieldType === "ENUM" && (
                <Select value={(val as string) ?? ""} onValueChange={(v) => setValue(field.fieldKey, v)}>
                  <SelectTrigger>
                    <SelectValue placeholder={`Select ${field.fieldLabel}`} />
                  </SelectTrigger>
                  <SelectContent>
                    {field.options?.map((opt) => (
                      <SelectItem key={opt} value={opt}>
                        {opt}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
              {field.fieldType === "URL" && <Input type="url" value={(val as string) ?? ""} onChange={(e) => setValue(field.fieldKey, e.target.value)} placeholder="https://..." />}
              {field.fieldType === "PHONE" && <Input type="tel" value={(val as string) ?? ""} onChange={(e) => setValue(field.fieldKey, e.target.value)} placeholder="+1 234..." />}
              {field.fieldType === "EMAIL" && <Input type="email" value={(val as string) ?? ""} onChange={(e) => setValue(field.fieldKey, e.target.value)} placeholder="name@example.com" />}
              {field.fieldType === "JSON" && (
                <Textarea
                  value={typeof val === "string" ? (val as string) : val !== undefined && val !== null ? JSON.stringify(val, null, 2) : ""}
                  onChange={(e) => setValue(field.fieldKey, e.target.value)}
                  placeholder='{"key": "value"}'
                  rows={4}
                  className="font-mono text-xs"
                />
              )}
              {field.fieldType === "REFERENCE" && (
                <ReferenceEntitySelector
                  value={(val as string) ?? null}
                  onChange={(v) => setValue(field.fieldKey, v ?? "")}
                  referenceEntityType={field.referenceEntityType || "LEAD"}
                  placeholder={`Search ${field.referenceEntityType || "reference"}…`}
                  required={field.isRequired}
                />
              )}
              {field.defaultValue && !initialData?.[field.fieldKey] && <p className="text-xs text-muted-foreground">Default: {field.defaultValue}</p>}
              {err && <FieldError>{err}</FieldError>}
            </Field>
          );
        })}
      <div className="flex gap-2 pt-2">
        <Button type="submit" disabled={isPending}>
          {isPending ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : null} {submitLabel}
        </Button>
      </div>
    </form>
  );
}
