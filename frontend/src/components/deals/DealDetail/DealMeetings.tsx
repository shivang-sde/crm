"use client";

import { useQuery } from "@tanstack/react-query";
import { Loader2, Users } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { meetingApi } from "@/lib/api/meetings";
import { MeetingResponse } from "@/types/meetings";

interface DealMeetingsProps {
  dealId: string;
}

export function DealMeetings({ dealId }: DealMeetingsProps) {
  const { data: result, isLoading } = useQuery({
    queryKey: ["meetings", "DEAL", dealId],
    queryFn: () => meetingApi.listMeetings({ entityType: "DEAL", entityId: dealId, size: 50 }),
    enabled: !!dealId,
  });

  const meetings = [...(result?.content ?? [])].sort((a, b) => {
    if (!a.startTime) return 1;
    if (!b.startTime) return -1;
    return new Date(b.startTime).getTime() - new Date(a.startTime).getTime();
  });

  return (
    <Card className="shadow-sm border border-muted">
      <CardHeader className="pb-2">
        <CardTitle className="text-base font-semibold text-foreground">Meetings</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="flex justify-center py-4">
            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
          </div>
        ) : meetings.length === 0 ? (
          <p className="text-sm text-muted-foreground">No meetings linked to this deal.</p>
        ) : (
          <ul className="space-y-2">
            {meetings.map((meeting: MeetingResponse) => (
              <li key={meeting.id} className="flex items-start justify-between gap-3 rounded-lg border px-3 py-2">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-foreground">{meeting.subject}</p>
                  <p className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
                    <Users className="h-3 w-3" />
                    {new Date(meeting.startTime).toLocaleString()}
                    {meeting.attendees && meeting.attendees.length > 0
                      ? ` · ${meeting.attendees.length} attendees`
                      : ""}
                  </p>
                </div>
                <Badge variant="outline" className="shrink-0 text-[11px]">
                  {meeting.status}
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
