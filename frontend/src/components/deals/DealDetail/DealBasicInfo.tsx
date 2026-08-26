"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { DealResponse } from "@/types/deals";
import { useAccount } from "@/lib/hooks/accounts";
import { useContact } from "@/lib/hooks/contacts";
import { useLead } from "@/lib/hooks/leads";
import { useUserLookup } from "@/lib/hooks/useUserLookup";
import Link from "next/link";
import { Calendar, DollarSign, Percent, User, Building2, UserCircle2, ArrowRightLeft } from "lucide-react";

interface DealBasicInfoProps {
  deal: DealResponse;
}

export function DealBasicInfo({ deal }: DealBasicInfoProps) {
  const { data: account } = useAccount(deal.accountId || undefined);
  const { data: contact } = useContact(deal.contactId || undefined);
  const { data: lead } = useLead(deal.leadId || undefined);
  const { resolveUserName } = useUserLookup();

  return (
    <Card className="shadow-sm border border-muted">
      <CardHeader className="pb-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <CardTitle className="text-2xl font-bold tracking-tight text-foreground">{deal.name}</CardTitle>
            <div className="flex flex-wrap gap-2 mt-2">
              <Badge variant="outline" className="bg-blue-50 text-blue-700 border-blue-200">
                Stage: {deal.stage?.name || "Pipeline"}
              </Badge>
              {deal.recordCategory === "CLOSED_WON" && (
                <Badge className="bg-emerald-100 text-emerald-800 hover:bg-emerald-100 border border-emerald-200">
                  Won
                </Badge>
              )}
              {deal.recordCategory === "CLOSED_LOST" && (
                <Badge className="bg-rose-100 text-rose-800 hover:bg-rose-100 border border-rose-200">
                  Lost
                </Badge>
              )}
              {deal.recordCategory === "OPEN" && (
                <Badge className="bg-amber-50 text-amber-800 hover:bg-amber-50 border border-amber-200">
                  Active
                </Badge>
              )}
            </div>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* Financial Information Section */}
        <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 border-b pb-6">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-blue-50 text-blue-600">
              <DollarSign className="h-5 w-5" />
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Amount</p>
              <p className="text-lg font-bold text-foreground">
                {deal.amount !== undefined && deal.amount !== null
                  ? `${deal.amount.toLocaleString()} ${deal.currency || "USD"}`
                  : "—"}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-emerald-50 text-emerald-600">
              <DollarSign className="h-5 w-5" />
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Expected Revenue</p>
              <p className="text-lg font-bold text-foreground">
                {deal.expectedRevenue !== undefined && deal.expectedRevenue !== null
                  ? `${deal.expectedRevenue.toLocaleString()} ${deal.currency || "USD"}`
                  : "—"}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-purple-50 text-purple-600">
              <Percent className="h-5 w-5" />
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Probability</p>
              <p className="text-lg font-bold text-foreground">
                {deal.probability !== undefined && deal.probability !== null ? `${deal.probability}%` : "—"}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-amber-50 text-amber-600">
              <Calendar className="h-5 w-5" />
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Expected Close</p>
              <p className="text-lg font-bold text-foreground">
                {deal.expectedCloseDate ? new Date(deal.expectedCloseDate).toLocaleDateString() : "—"}
              </p>
            </div>
          </div>
        </div>

        {/* Associated Records Section */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 border-b pb-6">
          {/* Associated Party */}
          <div className="space-y-4">
            <h4 className="text-sm font-semibold text-foreground uppercase tracking-wider">Associated Party</h4>
            
            <div className="space-y-3">
              <div className="flex items-center gap-2 text-sm">
                <Building2 className="h-4 w-4 text-muted-foreground" />
                <span className="text-muted-foreground min-w-[70px]">Account:</span>
                {deal.accountId ? (
                  account ? (
                    <Link href={`/accounts/${deal.accountId}`} className="text-primary hover:underline font-medium">
                      {account.name}
                    </Link>
                  ) : (
                    <span className="text-muted-foreground font-mono text-xs">{deal.accountId}</span>
                  )
                ) : (
                  <span className="text-muted-foreground italic">None</span>
                )}
              </div>

              <div className="flex items-center gap-2 text-sm">
                <UserCircle2 className="h-4 w-4 text-muted-foreground" />
                <span className="text-muted-foreground min-w-[70px]">Contact:</span>
                {deal.contactId ? (
                  contact ? (
                    <Link href={`/contacts/${deal.contactId}`} className="text-primary hover:underline font-medium">
                      {[contact.firstName, contact.lastName].filter(Boolean).join(" ")}
                    </Link>
                  ) : (
                    <span className="text-muted-foreground font-mono text-xs">{deal.contactId}</span>
                  )
                ) : (
                  <span className="text-muted-foreground italic">None</span>
                )}
              </div>

              <div className="flex items-center gap-2 text-sm">
                <ArrowRightLeft className="h-4 w-4 text-muted-foreground" />
                <span className="text-muted-foreground min-w-[70px]">Source Lead:</span>
                {deal.leadId ? (
                  lead ? (
                    <Link href={`/leads/${deal.leadId}`} className="text-primary hover:underline font-medium">
                      {[lead.firstName, lead.lastName].filter(Boolean).join(" ")}
                    </Link>
                  ) : (
                    <span className="text-muted-foreground font-mono text-xs">{deal.leadId}</span>
                  )
                ) : (
                  <span className="text-muted-foreground italic">None</span>
                )}
              </div>
            </div>
          </div>

          {/* Attribution & Context */}
          <div className="space-y-4">
            <h4 className="text-sm font-semibold text-foreground uppercase tracking-wider">Attribution & Context</h4>
            <div className="space-y-3 text-sm">
              <div>
                <span className="text-muted-foreground">Deal Type: </span>
                <span className="font-medium text-foreground">{deal.dealType || "—"}</span>
              </div>
              <div>
                <span className="text-muted-foreground">Lead Source: </span>
                <span className="font-medium text-foreground">{deal.leadSource || "—"}</span>
              </div>
              <div>
                <span className="text-muted-foreground">Campaign: </span>
                <span className="font-medium text-foreground">{deal.campaignSource || "—"}</span>
              </div>
              <div>
                <span className="text-muted-foreground">Forecast Category: </span>
                <span className="font-medium text-foreground">{deal.forecastCategory || "—"}</span>
              </div>
              <div>
                <span className="text-muted-foreground">Next Step: </span>
                <span className="font-medium text-foreground">{deal.nextStep || "—"}</span>
              </div>
            </div>
          </div>

          {/* Responsibility & Outcome */}
          <div className="space-y-4">
            <h4 className="text-sm font-semibold text-foreground uppercase tracking-wider">Responsibility & Outcome</h4>
            <div className="space-y-3 text-sm">
              <div className="flex items-center gap-2">
                <User className="h-4 w-4 text-muted-foreground" />
                <span className="text-muted-foreground">Owner:</span>
                <span className="font-medium text-foreground">
                  {deal.ownerUserId ? resolveUserName(deal.ownerUserId) : "Unassigned"}
                </span>
              </div>
              {deal.closedDate && (
                <div>
                  <span className="text-muted-foreground">Closed Date: </span>
                  <span className="font-medium text-foreground">{new Date(deal.closedDate).toLocaleDateString()}</span>
                </div>
              )}
              {deal.recordCategory === "CLOSED_WON" && deal.wonReason && (
                <div className="p-2.5 rounded bg-emerald-50 border border-emerald-100">
                  <p className="text-xs font-semibold text-emerald-800 uppercase tracking-wider">Won Reason</p>
                  <p className="text-sm text-emerald-700 mt-1">{deal.wonReason}</p>
                </div>
              )}
              {deal.recordCategory === "CLOSED_LOST" && deal.lostReason && (
                <div className="p-2.5 rounded bg-rose-50 border border-rose-100">
                  <p className="text-xs font-semibold text-rose-800 uppercase tracking-wider">Lost Reason</p>
                  <p className="text-sm text-rose-700 mt-1">{deal.lostReason}</p>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Audit Metadata */}
        <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
          <div>Created: {new Date(deal.createdAt).toLocaleString()}</div>
          <div>Last Updated: {new Date(deal.updatedAt).toLocaleString()}</div>
        </div>
      </CardContent>
    </Card>
  );
}
