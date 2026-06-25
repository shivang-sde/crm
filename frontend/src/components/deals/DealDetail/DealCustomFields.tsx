"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useDealCustomFields } from "@/lib/hooks/deals";
import { DealResponse } from "@/types/deals";

interface DealCustomFieldsProps {
  deal: DealResponse;
}

export function DealCustomFields({ deal }: DealCustomFieldsProps) {
  const { data: fieldDefs } = useDealCustomFields();
  const customData = deal.customData || {};

  const activeFields = (fieldDefs || [])
    .filter((f) => f.isActive && customData[f.fieldKey] !== undefined && customData[f.fieldKey] !== "")
    .sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0));

  if (activeFields.length === 0) return null;

  const formatValue = (key: string, value: unknown) => {
    if (Array.isArray(value)) return value.join(", ");
    if (typeof value === "boolean") return value ? "Yes" : "No";
    return String(value ?? "—");
  };

  return (
    <Card className="shadow-sm border border-muted">
      <CardHeader>
        <CardTitle className="text-base font-semibold text-foreground">Custom Fields</CardTitle>
      </CardHeader>
      <CardContent className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
        {activeFields.map((field) => (
          <div key={field.id} className="space-y-1">
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">{field.fieldLabel}</p>
            <p className="text-sm font-medium text-foreground">
              {formatValue(field.fieldKey, customData[field.fieldKey])}
            </p>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
