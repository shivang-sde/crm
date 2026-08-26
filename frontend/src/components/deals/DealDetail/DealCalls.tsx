"use client";

import { useQuery } from "@tanstack/react-query";
import { Loader2, Phone } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { callApi } from "@/lib/api/calls";
import { CallResponse } from "@/types/calls";

interface DealCallsProps {
  dealId: string;
}

export function DealCalls({ dealId }: DealCallsProps) {
  const { data: result, isLoading } = useQuery({
    queryKey: ["calls", "DEAL", dealId],
    queryFn: () => callApi.listCalls({ entityType: "DEAL", entityId: dealId, size: 50 }),
    enabled: !!dealId,
  });

  const calls = [...(result?.content ?? [])].sort((a, b) => {
    if (!a.startTime) return 1;
    if (!b.startTime) return -1;
    return new Date(b.startTime).getTime() - new Date(a.startTime).getTime();
  });

  return (
    <Card className="shadow-sm border border-muted">
      <CardHeader className="pb-2">
        <CardTitle className="text-base font-semibold text-foreground">Calls</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="flex justify-center py-4">
            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
          </div>
        ) : calls.length === 0 ? (
          <p className="text-sm text-muted-foreground">No calls linked to this deal.</p>
        ) : (
          <ul className="space-y-2">
            {calls.map((call: CallResponse) => (
              <li key={call.id} className="flex items-start justify-between gap-3 rounded-lg border px-3 py-2">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-foreground">{call.subject}</p>
                  <p className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
                    <Phone className="h-3 w-3" />
                    {call.startTime ? new Date(call.startTime).toLocaleString() : "Not started"}
                    {call.durationMinutes != null && call.durationMinutes > 0
                      ? ` · ${call.durationMinutes} min`
                      : ""}
                  </p>
                  {call.disposition && (
                    <p className="mt-0.5 truncate text-xs text-muted-foreground">{call.disposition}</p>
                  )}
                </div>
                <Badge variant="outline" className="shrink-0 text-[11px]">
                  {call.status}
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
