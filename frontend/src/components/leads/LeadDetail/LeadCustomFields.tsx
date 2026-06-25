import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useLeadCustomFields } from "@/lib/hooks/leads";
import { LeadResponse } from "@/types/leads";

interface LeadCustomFieldsProps {
  lead: LeadResponse;
}

export function LeadCustomFields({ lead }: LeadCustomFieldsProps) {
  const { data: fieldDefs } = useLeadCustomFields();
  const customData = lead.customData || {};

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
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Custom Fields</CardTitle>
      </CardHeader>
      <CardContent className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
        {activeFields.map((field) => (
          <div key={field.id}>
            <p className="text-muted-foreground">{field.fieldLabel}</p>
            <p>{formatValue(field.fieldKey, customData[field.fieldKey])}</p>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
