"use client";

import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Field, FieldLabel, FieldError } from "@/components/ui/field";
import { DealCustomFieldResponse } from "@/types/deal-custom-fields";

interface DynamicFieldInputProps {
  field: DealCustomFieldResponse;
  value: unknown;
  error?: string;
  onChange: (value: unknown) => void;
}

export function DynamicFieldInput({ field, value, error, onChange }: DynamicFieldInputProps) {
  const renderInput = () => {
    switch (field.fieldType) {
      case "TEXTAREA":
        return (
          <Textarea placeholder={field.fieldLabel} value={(value as string) || ""} onChange={(e) => onChange(e.target.value)} />
        );
      case "NUMBER":
        return (
          <Input type="number" placeholder={field.fieldLabel} value={value !== undefined && value !== null ? String(value) : ""} onChange={(e) => onChange(e.target.value === "" ? undefined : Number(e.target.value))} />
        );
      case "EMAIL":
        return (
          <Input type="email" placeholder={field.fieldLabel} value={(value as string) || ""} onChange={(e) => onChange(e.target.value)} />
        );
      case "PHONE":
        return (
          <Input type="tel" placeholder={field.fieldLabel} value={(value as string) || ""} onChange={(e) => onChange(e.target.value)} />
        );
      case "DATE":
        return <Input type="date" value={(value as string) || ""} onChange={(e) => onChange(e.target.value)} />;
      case "BOOLEAN":
        return <Checkbox checked={Boolean(value)} onCheckedChange={(checked) => onChange(checked === true)} />;
      case "SELECT":
        return (
          <Select value={(value as string) || ""} onValueChange={onChange}>
            <SelectTrigger>
              <SelectValue placeholder={`Select ${field.fieldLabel}`} />
            </SelectTrigger>
            <SelectContent>
              {field.options?.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        );
      case "MULTISELECT": {
        const selected = (value as string[]) || [];
        return (
          <div className="space-y-2">
            {field.options?.map((opt) => (
              <label key={opt.value} className="flex items-center gap-2 text-sm cursor-pointer">
                <Checkbox checked={selected.includes(opt.value)} onCheckedChange={(checked) => {
                  onChange(checked ? [...selected, opt.value] : selected.filter((v) => v !== opt.value));
                }} />
                {opt.label}
              </label>
            ))}
          </div>
        );
      }
      case "URL":
        return <Input type="url" placeholder={field.fieldLabel} value={(value as string) || ""} onChange={(e) => onChange(e.target.value)} />;
      default:
        return <Input placeholder={field.fieldLabel} value={(value as string) || ""} onChange={(e) => onChange(e.target.value)} />;
    }
  };

  return (
    <Field>
      <FieldLabel>
        {field.fieldLabel}
        {field.isRequired && <span className="text-red-500 ml-1">*</span>}
      </FieldLabel>
      {renderInput()}
      {error && <FieldError>{error}</FieldError>}
    </Field>
  );
}
