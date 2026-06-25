import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { StatusBadge } from "../shared/StatusBadge";
import { SourceBadge } from "../shared/SourceBadge";
import { LeadResponse } from "@/types/leads";

interface LeadBasicInfoProps {
  lead: LeadResponse;
}

export function LeadBasicInfo({ lead }: LeadBasicInfoProps) {
  const name = [lead.firstName, lead.lastName].filter(Boolean).join(" ");

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl">{name}</CardTitle>
        <div className="flex flex-wrap gap-2 pt-1">
          <StatusBadge status={lead.status} />
          <SourceBadge source={lead.source} />
          {lead.isConverted && (
            <span className="text-xs bg-green-100 text-green-800 px-2 py-0.5 rounded-full">
              Converted
            </span>
          )}
        </div>
      </CardHeader>
      <CardContent className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
        <div>
          <p className="text-muted-foreground">Email</p>
          <p>{lead.email || "—"}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Phone</p>
          <p>{lead.phone || "—"}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Company</p>
          <p>{lead.company || "—"}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Score</p>
          <p>{lead.score ?? 0} / 100</p>
        </div>
        {lead.notes && (
          <div className="sm:col-span-2">
            <p className="text-muted-foreground">Summary notes</p>
            <p className="whitespace-pre-wrap">{lead.notes}</p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
