"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useAssignLead, useChangeLeadStatus, useLeadStatuses } from "@/lib/hooks/leads";
import { userApi } from "@/lib/api/users";
import { useQuery } from "@tanstack/react-query";
import { LeadResponse } from "@/types/leads";
import { usePermissions } from "@/lib/hooks/usePermissions";

interface LeadAssignmentProps {
  lead: LeadResponse;
}

export function LeadAssignment({ lead }: LeadAssignmentProps) {
  const { canEditLeads } = usePermissions();
  const { data: statuses } = useLeadStatuses();
  const assignMutation = useAssignLead();
  const statusMutation = useChangeLeadStatus();

  const { data: usersData } = useQuery({
    queryKey: ["users", "lead-assign"],
    queryFn: () => userApi.getUsers({ page: 0, isActive: true }),
    enabled: canEditLeads,
  });

  if (!canEditLeads) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Assignment</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          Owner: {lead.ownerUserId || "Unassigned"}
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Manage</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-row items-center space-x-8 text-sm text-muted-foreground">
        <div>
          <p className="text-sm text-muted-foreground mb-2">Status</p>
          <Select
            value={lead.status.id}
            onValueChange={(statusId) =>
              statusMutation.mutate({ id: lead.id, statusId })
            }
            disabled={statusMutation.isPending}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {statuses?.map((s) => (
                <SelectItem key={s.id} value={s.id}>
                  {s.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div>
          <p className="text-sm text-muted-foreground mb-2">Owner</p>
          <Select
            value={lead.ownerUserId || "none"}
            onValueChange={(ownerUserId) => {
              if (ownerUserId !== "none") {
                assignMutation.mutate({ id: lead.id, ownerUserId });
              }
            }}
            disabled={assignMutation.isPending}
          >
            <SelectTrigger>
              <SelectValue placeholder="Unassigned" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="none">Unassigned</SelectItem>
              {usersData?.content.map((user) => (
                <SelectItem key={user.id} value={user.id}>
                  {user.firstName} {user.lastName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </CardContent>
    </Card>
  );
}
