"use client";

import Link from "next/link";
import { useDeals } from "@/lib/hooks/deals";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { useUserLookup } from "@/lib/hooks/useUserLookup";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { DealResponse } from "@/types/deals";

interface EntityDealsSectionProps {
  entityType: "ACCOUNT" | "CONTACT";
  entityId: string;
}

function formatAmount(deal: DealResponse): string {
  if (deal.amount === undefined || deal.amount === null) return "—";
  return `${deal.amount.toLocaleString()} ${deal.currency || "USD"}`;
}

function categoryBadge(category?: string | null) {
  if (category === "CLOSED_WON") {
    return <Badge className="bg-emerald-100 text-emerald-800 hover:bg-emerald-100 border border-emerald-200">Won</Badge>;
  }
  if (category === "CLOSED_LOST") {
    return <Badge className="bg-rose-100 text-rose-800 hover:bg-rose-100 border border-rose-200">Lost</Badge>;
  }
  if (category === "OPEN") {
    return <Badge className="bg-amber-50 text-amber-800 hover:bg-amber-50 border border-amber-200">Active</Badge>;
  }
  return null;
}

export function EntityDealsSection({ entityType, entityId }: EntityDealsSectionProps) {
  const { canViewDeals } = usePermissions();
  const { resolveUserName } = useUserLookup();
  const filter = entityType === "ACCOUNT" ? { accountId: entityId } : { contactId: entityId };
  const { data: dealsResult, isLoading, isError } = useDeals({ page: 0, size: 50, ...filter });

  if (!canViewDeals) {
    return null;
  }

  const deals = dealsResult?.data ?? [];
  const openDeals = deals.filter((deal) => deal.recordCategory === "OPEN");
  const wonDeals = deals.filter((deal) => deal.recordCategory === "CLOSED_WON");
  const openPipeline = openDeals.reduce((sum, deal) => sum + (deal.amount ?? 0), 0);
  const wonValue = wonDeals.reduce((sum, deal) => sum + (deal.amount ?? 0), 0);

  const summary =
    deals.length > 0
      ? `Open ${openDeals.length} (${openPipeline.toLocaleString()}) · Won ${wonDeals.length} (${wonValue.toLocaleString()}) · Lost ${deals.length - openDeals.length - wonDeals.length}`
      : undefined;

  let content;
  if (isLoading) {
    content = <div className="text-sm text-muted-foreground">Loading deals…</div>;
  } else if (isError) {
    content = <div className="text-sm text-destructive">Unable to load deals.</div>;
  } else if (deals.length === 0) {
    content = (
      <div className="text-sm text-muted-foreground">
        No deals found for this {entityType.toLowerCase()}.
      </div>
    );
  } else {
    content = (
      <>
        {summary && (
          <p className="px-6 py-2 text-xs text-muted-foreground border-b bg-muted/50">{summary}</p>
        )}
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Deal</TableHead>
              <TableHead>Stage</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Amount</TableHead>
              <TableHead>Expected close</TableHead>
              <TableHead>Owner</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {deals.map((deal) => (
              <TableRow key={deal.id}>
                <TableCell className="font-medium">
                  <Link href={`/deals/${deal.id}`} className="text-primary hover:underline">
                    {deal.name}
                  </Link>
                </TableCell>
                <TableCell>{deal.stage?.name || "—"}</TableCell>
                <TableCell>{categoryBadge(deal.recordCategory)}</TableCell>
                <TableCell>{formatAmount(deal)}</TableCell>
                <TableCell>
                  {deal.expectedCloseDate
                    ? new Date(deal.expectedCloseDate).toLocaleDateString()
                    : "—"}
                </TableCell>
                <TableCell>
                  <span className="block max-w-[140px] truncate">
                    {deal.ownerUserId ? resolveUserName(deal.ownerUserId) : "Unassigned"}
                  </span>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </>
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border">
      <div className="flex items-center justify-between border-b bg-muted px-6 py-3">
        <p className="text-sm font-medium text-muted-foreground">Deals</p>
      </div>
      {content}
    </div>
  );
}
