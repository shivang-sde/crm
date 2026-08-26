"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { CalendarRange, Search, ShieldCheck, Sparkles } from "lucide-react";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useEntitlements } from "@/lib/hooks/entitlements";
import { useUserLookup } from "@/lib/hooks/useUserLookup";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { CustomerEntitlementResponse, EntitlementStatus } from "@/types/entitlements";

function getExpiryBadge(entitlement: CustomerEntitlementResponse) {
  if (!entitlement.end_date) {
    return { label: "No expiry", tone: "secondary" as const };
  }

  const endDate = new Date(entitlement.end_date);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diffDays = Math.ceil((endDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));

  if (diffDays < 0) {
    return { label: "Expired", tone: "destructive" as const };
  }
  if (diffDays <= 7) {
    return { label: `Expires in ${diffDays} day${diffDays === 1 ? "" : "s"}`, tone: "destructive" as const };
  }
  if (diffDays <= 30) {
    return { label: `Expires in ${diffDays} days`, tone: "outline" as const };
  }
  return { label: `Active until ${endDate.toLocaleDateString()}`, tone: "secondary" as const };
}

function getStatusBadge(status?: EntitlementStatus | null) {
  switch (status) {
    case "ACTIVE":
      return <Badge className="bg-emerald-600 hover:bg-emerald-700">Active</Badge>;
    case "PENDING":
      return <Badge variant="outline">Pending</Badge>;
    case "SUSPENDED":
      return <Badge variant="secondary">Suspended</Badge>;
    case "EXPIRED":
      return <Badge variant="destructive">Expired</Badge>;
    case "CANCELLED":
      return <Badge variant="outline">Cancelled</Badge>;
    case "RENEWED":
      return <Badge className="bg-sky-600 hover:bg-sky-700">Renewed</Badge>;
    case "TERMINATED":
      return <Badge variant="secondary">Terminated</Badge>;
    default:
      return <Badge variant="outline">Unknown</Badge>;
  }
}

function EntitlementsPageContent() {
  const searchParams = useSearchParams();
  const { canEditEntitlements } = usePermissions();
  const [search, setSearch] = useState(searchParams.get("search") ?? "");
  const [status, setStatus] = useState<EntitlementStatus | "ALL">((searchParams.get("status") as EntitlementStatus | null) ?? "ALL");
  const [renewable, setRenewable] = useState<string>(searchParams.get("renewable") ?? "ALL");
  const [ownerUserId, setOwnerUserId] = useState(searchParams.get("ownerUserId") ?? "");
  const [endDateFrom, setEndDateFrom] = useState(searchParams.get("endDateFrom") ?? "");
  const [endDateTo, setEndDateTo] = useState(searchParams.get("endDateTo") ?? "");

  const params = useMemo(() => ({
    search: search || undefined,
    status: status === "ALL" ? undefined : status,
    renewable: renewable === "ALL" ? undefined : renewable === "true",
    ownerUserId: ownerUserId || undefined,
    endDateFrom: endDateFrom || undefined,
    endDateTo: endDateTo || undefined,
    size: 50,
  }), [search, status, renewable, ownerUserId, endDateFrom, endDateTo]);

  const { data, isLoading } = useEntitlements(params);
  const entitlements = useMemo(() => data?.data ?? [], [data]);
  const { resolveUserName, users } = useUserLookup();

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Customer Products & Services</h1>
          <p className="text-sm text-muted-foreground">Products and services provisioned from closed-won deals.</p>
        </div>
        {canEditEntitlements ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <ShieldCheck className="h-4 w-4" />
            Update and lifecycle actions enabled
          </div>
        ) : null}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Filters</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
            <div className="relative md:col-span-2">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search by name, code, or description"
                className="pl-9"
              />
            </div>
            <Select value={status} onValueChange={(value) => setStatus(value as EntitlementStatus | "ALL")}>
              <SelectTrigger>
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All statuses</SelectItem>
                <SelectItem value="PENDING">Pending</SelectItem>
                <SelectItem value="ACTIVE">Active</SelectItem>
                <SelectItem value="SUSPENDED">Suspended</SelectItem>
                <SelectItem value="EXPIRED">Expired</SelectItem>
                <SelectItem value="CANCELLED">Cancelled</SelectItem>
                <SelectItem value="RENEWED">Renewed</SelectItem>
                <SelectItem value="TERMINATED">Terminated</SelectItem>
              </SelectContent>
            </Select>
            <Select value={renewable} onValueChange={setRenewable}>
              <SelectTrigger>
                <SelectValue placeholder="Renewable" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All</SelectItem>
                <SelectItem value="true">Renewable</SelectItem>
                <SelectItem value="false">Non-renewable</SelectItem>
              </SelectContent>
            </Select>
            <Select value={ownerUserId || "all"} onValueChange={(value) => setOwnerUserId(value === "all" ? "" : value)}>
              <SelectTrigger className="w-full sm:w-[180px]">
                <SelectValue placeholder="All owners" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All owners</SelectItem>
                {users.map((user) => (
                  <SelectItem key={user.id} value={user.id}>
                    {resolveUserName(user.id)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Input type="date" value={endDateFrom} onChange={(event) => setEndDateFrom(event.target.value)} />
            <Input type="date" value={endDateTo} onChange={(event) => setEndDateTo(event.target.value)} />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between gap-2">
            <CardTitle>Entitlements</CardTitle>
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <CalendarRange className="h-4 w-4" />
              Expiry visibility is shown inline
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-muted-foreground">Loading entitlements…</p>
          ) : entitlements.length === 0 ? (
            <div className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
              <p className="font-medium text-foreground">No customer products or services found.</p>
              <p className="mt-1">Entitlements appear automatically after a deal is marked as closed won.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Customer</TableHead>
                    <TableHead>Offering</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Quantity</TableHead>
                    <TableHead>Start Date</TableHead>
                    <TableHead>End Date</TableHead>
                    <TableHead>Renewable</TableHead>
                    <TableHead>Owner</TableHead>
                    <TableHead>Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {entitlements.map((entitlement) => {
                    const expiry = getExpiryBadge(entitlement);
                    return (
                      <TableRow key={entitlement.id}>
                        <TableCell>
                          <div className="space-y-1">
                            <Link href={`/entitlements/${entitlement.id}`} className="font-medium text-primary hover:underline">
                              {entitlement.name || entitlement.code || "Untitled entitlement"}
                            </Link>
                            {entitlement.code ? <p className="text-xs text-muted-foreground">{entitlement.code}</p> : null}
                          </div>
                        </TableCell>
                        <TableCell>{entitlement.account_id ? <Link href={`/accounts/${entitlement.account_id}`} className="text-primary hover:underline">Account</Link> : "—"}</TableCell>
                        <TableCell>{entitlement.offering_id ? <Link href="/offerings" className="text-primary hover:underline">Offering</Link> : "—"}</TableCell>
                        <TableCell>{getStatusBadge(entitlement.status)}</TableCell>
                        <TableCell>{entitlement.quantity ?? "—"}</TableCell>
                        <TableCell>{entitlement.start_date ? new Date(entitlement.start_date).toLocaleDateString() : "—"}</TableCell>
                        <TableCell>
                          <div className="space-y-1">
                            <p>{entitlement.end_date ? new Date(entitlement.end_date).toLocaleDateString() : "—"}</p>
                            <Badge variant={expiry.tone === "destructive" ? "destructive" : expiry.tone === "secondary" ? "secondary" : "outline"} className="text-[11px]">
                              {expiry.label}
                            </Badge>
                          </div>
                        </TableCell>
                        <TableCell>{entitlement.renewable ? "Yes" : "No"}</TableCell>
                        <TableCell>{entitlement.owner_user_id ? resolveUserName(entitlement.owner_user_id) : "Unassigned"}</TableCell>
                        <TableCell>
                          <Button variant="outline" size="sm" asChild>
                            <Link href={`/entitlements/${entitlement.id}`}>View</Link>
                          </Button>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

export default function EntitlementsPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "entitlement", action: "read" }}>
      <EntitlementsPageContent />
    </ProtectedRoute>
  );
}
