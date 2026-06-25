import { Badge } from "@/components/ui/badge";
import { LeadSourceSummary } from "@/types/leads";

interface SourceBadgeProps {
  source?: LeadSourceSummary;
}

export function SourceBadge({ source }: SourceBadgeProps) {
  if (!source) {
    return <span className="text-sm text-muted-foreground">—</span>;
  }
  return (
    <Badge variant="secondary" className="font-normal">
      {source.name}
    </Badge>
  );
}
