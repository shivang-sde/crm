"use client";

import { useMemo } from "react";
import { DealCustomFieldResponse } from "@/types/deal-custom-fields";
import { DynamicFieldInput } from "./DynamicFieldInput";

interface DynamicFieldRendererProps {
  fields: DealCustomFieldResponse[];
  values: Record<string, unknown>;
  errors?: Record<string, string>;
  onChange: (fieldKey: string, value: unknown) => void;
}

export function DynamicFieldRenderer({ fields, values, errors = {}, onChange }: DynamicFieldRendererProps) {
  const sortedFields = useMemo(
    () => fields.filter((f) => f.isActive).sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0)),
    [fields]
  );

  if (sortedFields.length === 0) return null;

  return (
    <div className="space-y-4">
      {sortedFields.map((field) => (
        <DynamicFieldInput
          key={field.id}
          field={field}
          value={values[field.fieldKey]}
          error={errors[field.fieldKey]}
          onChange={(value) => onChange(field.fieldKey, value)}
        />
      ))}
    </div>
  );
}
