"use client";

import Link from "next/link";
import {
  Eye,
  MoreHorizontal,
  Pencil,
  Phone,
  UserRoundCheck,
} from "lucide-react";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

import { StatusBadge } from "../shared/StatusBadge";
import { SourceBadge } from "../shared/SourceBadge";
import { LeadConvertDialog } from "../LeadConvertDialog";

import { usePermissions } from "@/lib/hooks/usePermissions";
import type { LeadResponse } from "@/types/leads";
import { ClickToCallButton } from "@/components/call-opening/ClickToCallButton";

interface LeadTableProps {
  leads: LeadResponse[];
}

function formatName(lead: LeadResponse) {
  const name = [lead.firstName, lead.lastName]
    .filter(Boolean)
    .join(" ")
    .trim();

  return name || "Unnamed lead";
}

export function LeadTable({ leads }: LeadTableProps) {
  const { canEditLeads } = usePermissions();

  return (
    <div className="overflow-hidden rounded-lg border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="min-w-[180px]">
              Name
            </TableHead>

            <TableHead className="min-w-[200px]">
              Email
            </TableHead>

            <TableHead className="min-w-[140px]">
              Phone
            </TableHead>

            <TableHead className="min-w-[150px]">
              Company
            </TableHead>

            <TableHead>Status</TableHead>
            <TableHead>Source</TableHead>

            <TableHead className="min-w-[120px]">
              Score
            </TableHead>

            <TableHead className="min-w-[110px]">
              Created
            </TableHead>

            <TableHead className="w-[80px] text-right">
              Actions
            </TableHead>
          </TableRow>
        </TableHeader>

        <TableBody>
          {leads.map((lead) => (
            <TableRow key={lead.id}>
              <TableCell className="font-medium">
                <Link
                  href={`/leads/${lead.id}`}
                  className="hover:text-primary hover:underline"
                >
                  {formatName(lead)}
                </Link>
              </TableCell>

              <TableCell>
                <span className="block max-w-[220px] truncate">
                  {lead.email || "—"}
                </span>
              </TableCell>

              <TableCell>
                {lead.phone || "—"}
              </TableCell>

              <TableCell>
                <span className="block max-w-[180px] truncate">
                  {lead.company || "—"}
                </span>
              </TableCell>

              <TableCell>
                <StatusBadge status={lead.status} />
              </TableCell>

              <TableCell>
                <SourceBadge source={lead.source} />
              </TableCell>

              <TableCell>
                <div className="flex min-w-[90px] items-center gap-2">
                  <div className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-primary"
                      style={{
                        width: `${Math.min(
                          Math.max(lead.score ?? 0, 0),
                          100
                        )}%`,
                      }}
                    />
                  </div>

                  <span className="whitespace-nowrap text-xs text-muted-foreground">
                    {lead.score ?? 0}
                  </span>
                </div>
              </TableCell>

              <TableCell className="text-sm text-muted-foreground">
                {new Date(
                  lead.createdAt
                ).toLocaleDateString()}
              </TableCell>

              <TableCell className="text-right">
                <div className="flex items-center justify-end gap-1">
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

                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`Actions for ${formatName(
                          lead
                        )}`}
                      >
                        <MoreHorizontal className="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>

                    <DropdownMenuContent
                      align="end"
                      className="w-48"
                    >
                      <DropdownMenuLabel>
                        Lead actions
                      </DropdownMenuLabel>

                      <DropdownMenuSeparator />

                      <DropdownMenuItem asChild>
                        <Link href={`/leads/${lead.id}`}>
                          <Eye className="mr-2 h-4 w-4" />
                          View details
                        </Link>
                      </DropdownMenuItem>

                      {canEditLeads && (
                        <DropdownMenuItem asChild>
                          <Link
                            href={`/leads/${lead.id}/edit`}
                          >
                            <Pencil className="mr-2 h-4 w-4" />
                            Edit lead
                          </Link>
                        </DropdownMenuItem>
                      )}

                      {lead.phone && (
                        <DropdownMenuItem asChild>
                          <a href={`tel:${lead.phone}`}>
                            <Phone className="mr-2 h-4 w-4" />
                            Call from device
                          </a>
                        </DropdownMenuItem>
                      )}

                      {!lead.isConverted &&
                        canEditLeads && (
                          <>
                            <DropdownMenuSeparator />

                            <div className="px-2 py-1">
                              <LeadConvertDialog
                                lead={lead}
                                triggerLabel="Convert lead"
                                triggerVariant="ghost"
                                triggerClassName="h-8 w-full justify-start px-2 font-normal"
                              />
                            </div>
                          </>
                        )}

                      {lead.isConverted && (
                        <>
                          <DropdownMenuSeparator />

                          <DropdownMenuItem disabled>
                            <UserRoundCheck className="mr-2 h-4 w-4" />
                            Already converted
                          </DropdownMenuItem>
                        </>
                      )}
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}