"use client";

import { Loader2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useAccountActivities } from "@/lib/hooks/accounts";

interface AccountTimelineProps {
  accountId: string;
}

export function AccountTimeline({ accountId }: AccountTimelineProps) {
  const { data: result, isLoading } = useAccountActivities(accountId);
  const activities = result?.data ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Activity Timeline</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="flex justify-center py-6">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : activities.length === 0 ? (
          <p className="text-sm text-muted-foreground">No activity yet.</p>
        ) : (
          <ul className="space-y-4">
            {activities.map((activity) => (
              <li key={activity.id} className="border-l-2 border-primary/30 pl-4">
                <p className="text-sm font-medium">{activity.description}</p>
                <p className="text-xs text-muted-foreground mt-0.5">
                  {activity.activityType.replace(/_/g, " ")} · {new Date(activity.createdAt).toLocaleString()}
                </p>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
