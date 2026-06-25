import { Badge } from "@/components/ui/badge";
import { LeadStatusSummary } from "@/types/leads";

interface StatusBadgeProps {
  status: LeadStatusSummary;
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const color = status.color || "#6366f1";
  return (
    <Badge
      variant="outline"
      className="font-normal"
      style={{
        borderColor: color,
        color,
        backgroundColor: `${color}15`,
      }}
    >
      {status.name}
    </Badge>
  );
}
