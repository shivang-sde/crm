"use client";

import { useState } from "react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { DealResponse } from "@/types/deals";
import Link from "next/link";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { useDeleteDeal } from "@/lib/hooks/deals";
import { useAccounts } from "@/lib/hooks/accounts";
import { useContacts } from "@/lib/hooks/contacts";
import { useQuery } from "@tanstack/react-query";
import { userApi } from "@/lib/api/users";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Trash2, Edit, Eye, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { EditDealDialog } from "../DealForm/EditDealDialog";

interface DealTableProps {
  deals: DealResponse[];
}

export function DealTable({ deals }: DealTableProps) {
  const { hasPermission, canEditDeals } = usePermissions();
  const canDeleteDeals = hasPermission("deal", "delete");

  const deleteMutation = useDeleteDeal();

  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [isDeleting, setIsDeleting] = useState(false);

  // Fetch lookups
  const { data: accountsData } = useAccounts({ page: 0, size: 100 });
  const { data: contactsData } = useContacts({ page: 0, size: 100 });
  const { data: usersData } = useQuery({
    queryKey: ["users", "deal-table"],
    queryFn: () => userApi.getUsers({ page: 0, isActive: true }),
  });

  const accountsMap = new Map(accountsData?.data.map((a) => [a.id, a.name]) ?? []);
  const contactsMap = new Map(contactsData?.data.map((c) => [c.id, [c.firstName, c.lastName].filter(Boolean).join(" ")]) ?? []);
  const usersMap = new Map(usersData?.content.map((u: any) => [u.id, `${u.firstName} ${u.lastName}`]) ?? []);

  // Selection handlers
  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      setSelectedIds(new Set(deals.map((deal) => deal.id)));
    } else {
      setSelectedIds(new Set());
    }
  };

  const handleSelectOne = (id: string, checked: boolean) => {
    const next = new Set(selectedIds);
    if (checked) {
      next.add(id);
    } else {
      next.delete(id);
    }
    setSelectedIds(next);
  };

  const handleBulkDelete = async () => {
    if (selectedIds.size === 0) return;
    if (window.confirm(`Are you sure you want to delete the ${selectedIds.size} selected deals?`)) {
      setIsDeleting(true);
      try {
        await Promise.all(Array.from(selectedIds).map((id) => deleteMutation.mutateAsync(id)));
        setSelectedIds(new Set());
        toast.success("Selected deals deleted successfully");
      } catch (err) {
        toast.error("Failed to delete some deals");
      } finally {
        setIsDeleting(false);
      }
    }
  };

  const isAllSelected = deals.length > 0 && selectedIds.size === deals.length;

  return (
    <div className="space-y-4">
      {/* Floating/Sticky Action Bar for Bulk Selection */}
      {selectedIds.size > 0 && (
        <div className="flex items-center justify-between bg-blue-50 border border-blue-200 rounded-md p-3 px-4 animate-in fade-in slide-in-from-top-2 duration-200">
          <span className="text-sm font-medium text-blue-800">
            {selectedIds.size} {selectedIds.size === 1 ? "deal" : "deals"} selected
          </span>
          {canDeleteDeals && (
            <Button
              variant="destructive"
              size="sm"
              onClick={handleBulkDelete}
              disabled={isDeleting}
              className="flex items-center gap-1.5"
            >
              {isDeleting ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <Trash2 className="h-3.5 w-3.5" />
              )}
              {isDeleting ? "Deleting..." : "Delete Selected"}
            </Button>
          )}
        </div>
      )}

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-[50px]">
              <Checkbox
                checked={isAllSelected}
                onCheckedChange={(checked) => handleSelectAll(!!checked)}
                aria-label="Select all"
              />
            </TableHead>
            <TableHead>Name</TableHead>
            <TableHead>Amount</TableHead>
            <TableHead>Exp. Revenue</TableHead>
            <TableHead>Stage</TableHead>
            <TableHead>Forecast</TableHead>
            <TableHead>Account</TableHead>
            <TableHead>Contact</TableHead>
            <TableHead>Owner</TableHead>
            <TableHead>Created</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {deals.map((deal) => {
            const isSelected = selectedIds.has(deal.id);
            const accountName = deal.accountId ? accountsMap.get(deal.accountId) : null;
            const contactName = deal.contactId ? contactsMap.get(deal.contactId) : null;
            const ownerName = deal.ownerUserId ? usersMap.get(deal.ownerUserId) : null;

            return (
              <TableRow key={deal.id} className={isSelected ? "bg-muted/40" : undefined}>
                <TableCell>
                  <Checkbox
                    checked={isSelected}
                    onCheckedChange={(checked) => handleSelectOne(deal.id, !!checked)}
                    aria-label={`Select ${deal.name}`}
                  />
                </TableCell>
                <TableCell className="font-medium">
                  <Link href={`/deals/${deal.id}`} className="text-primary hover:underline font-semibold">
                    {deal.name}
                  </Link>
                </TableCell>
                <TableCell>
                  {deal.amount !== undefined && deal.amount !== null
                    ? `${deal.amount.toLocaleString()} ${deal.currency || "USD"}`
                    : "—"}
                </TableCell>
                <TableCell className="text-emerald-700 font-medium">
                  {deal.expectedRevenue !== undefined && deal.expectedRevenue !== null && deal.expectedRevenue > 0
                    ? `${deal.expectedRevenue.toLocaleString()}`
                    : "—"}
                </TableCell>
                <TableCell>
                  <span
                    className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border"
                    style={{
                      backgroundColor: deal.recordCategory === "CLOSED_WON"
                        ? "#d1fae5"
                        : deal.recordCategory === "CLOSED_LOST"
                        ? "#ffe4e6"
                        : "#f1f5f9",
                      color: deal.recordCategory === "CLOSED_WON"
                        ? "#065f46"
                        : deal.recordCategory === "CLOSED_LOST"
                        ? "#9f1239"
                        : "#475569",
                      borderColor: deal.recordCategory === "CLOSED_WON"
                        ? "#a7f3d0"
                        : deal.recordCategory === "CLOSED_LOST"
                        ? "#fecdd3"
                        : "#e2e8f0",
                    }}
                  >
                    {deal.stage?.name || "Pipeline"}
                  </span>
                </TableCell>
                <TableCell className="text-xs text-muted-foreground">
                  {deal.forecastCategory || "—"}
                </TableCell>
                <TableCell>
                  {deal.accountId ? (
                    accountName ? (
                      <Link href={`/accounts/${deal.accountId}`} className="text-primary hover:underline text-sm font-medium">
                        {accountName}
                      </Link>
                    ) : (
                      <span className="text-muted-foreground text-xs font-mono">{deal.accountId.slice(0, 8)}...</span>
                    )
                  ) : (
                    "—"
                  )}
                </TableCell>
                <TableCell>
                  {deal.contactId ? (
                    contactName ? (
                      <Link href={`/contacts/${deal.contactId}`} className="text-primary hover:underline text-sm font-medium">
                        {contactName}
                      </Link>
                    ) : (
                      <span className="text-muted-foreground text-xs font-mono">{deal.contactId.slice(0, 8)}...</span>
                    )
                  ) : (
                    "—"
                  )}
                </TableCell>
                <TableCell className="text-sm">
                  {ownerName || (deal.ownerUserId ? "Assigned" : "Unassigned")}
                </TableCell>
                <TableCell className="text-xs text-muted-foreground">
                  {new Date(deal.createdAt).toLocaleDateString()}
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end gap-1.5">
                    <Button variant="ghost" size="icon" asChild>
                      <Link href={`/deals/${deal.id}`} title="View Details">
                        <Eye className="h-4 w-4 text-muted-foreground" />
                      </Link>
                    </Button>
                    {canEditDeals && (
                      <EditDealDialog deal={deal} />
                    )}
                    {canDeleteDeals && (
                      <Button
                        variant="ghost"
                        size="icon"
                        className="text-destructive"
                        onClick={() => {
                          if (window.confirm(`Delete deal "${deal.name}"?`)) {
                            deleteMutation.mutate(deal.id);
                          }
                        }}
                        disabled={deleteMutation.isPending}
                        title="Delete Deal"
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
