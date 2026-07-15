"use client";

import Link from "next/link";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { StatusBadge } from "../shared/StatusBadge";
import { SourceBadge } from "../shared/SourceBadge";
import { LeadConvertDialog } from "../LeadConvertDialog";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { LeadResponse } from "@/types/leads";
import { ClickToCallButton } from "@/components/call-opening/ClickToCallButton";

interface LeadTableProps {
  leads: LeadResponse[];
}

function formatName(lead: LeadResponse) {
  return [lead.firstName, lead.lastName].filter(Boolean).join(" ");
}

export function LeadTable({ leads }: LeadTableProps) {
  const { canEditLeads } = usePermissions();

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead>Email</TableHead>
          <TableHead>Phone</TableHead>
          <TableHead>Company</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Source</TableHead>
          <TableHead>Score</TableHead>
          <TableHead>Created</TableHead>
          <TableHead>Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {leads.map((lead) => (
          <TableRow key={lead.id}>
            <TableCell className="font-medium">
              <Link
                href={`/leads/${lead.id}`}
                className="text-primary hover:underline"
              >
                {formatName(lead)}
              </Link>
            </TableCell>
            <TableCell>{lead.email || "—"}</TableCell>
            <TableCell>{lead.phone || "—"}</TableCell>
            <TableCell>{lead.company || "—"}</TableCell>
            <TableCell>
              <StatusBadge status={lead.status} />
            </TableCell>
            <TableCell>
              <SourceBadge source={lead.source} />
            </TableCell>
            <TableCell>
              <div className="flex items-center gap-2 min-w-[80px]">
                <div className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
                  <div
                    className="h-full bg-primary rounded-full"
                    style={{ width: `${Math.min(lead.score ?? 0, 100)}%` }}
                  />
                </div>
                <span className="text-xs text-muted-foreground whitespace-nowrap">
                  {lead.score ?? 0}
                </span>
              </div>
            </TableCell>
            <TableCell className="text-sm text-muted-foreground">
              {new Date(lead.createdAt).toLocaleDateString()}
            </TableCell>
            <TableCell className="space-y-2">
              {lead.phone && (
                <ClickToCallButton
                  entityType="lead"
                  entityId={lead.id}
                  phoneNumber={lead.phone}
                  label="Call"
                  variant="ghost"
                  size="icon"
                />
              )}
              {lead.isConverted ? (
                <span className="text-muted-foreground text-sm">Converted</span>
              ) : canEditLeads ? (
                <LeadConvertDialog lead={lead} triggerLabel="Convert" />
              ) : (
                <span className="text-muted-foreground text-sm">No access</span>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
