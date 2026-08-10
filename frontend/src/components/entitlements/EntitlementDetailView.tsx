"use client";

import Link from "next/link";
import { CalendarRange, CircleAlert, Package2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CustomerEntitlementResponse, EntitlementStatus } from "@/types/entitlements";

interface EntitlementDetailViewProps {
  entitlement: CustomerEntitlementResponse;
  canEdit?: boolean;
}

function getExpiryLabel(entitlement: CustomerEntitlementResponse) {
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

export function EntitlementDetailView({ entitlement }: EntitlementDetailViewProps) {
  const expiry = getExpiryLabel(entitlement);

  return (
    <div className="grid gap-6 lg:grid-cols-[2fr_1fr]">
      <div className="space-y-6">
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <CardTitle className="flex items-center gap-2">
                  <Package2 className="h-5 w-5" />
                  {entitlement.name || entitlement.code || "Untitled entitlement"}
                </CardTitle>
                <p className="mt-2 text-sm text-muted-foreground">
                  {entitlement.description || "No description provided."}
                </p>
              </div>
              {getStatusBadge(entitlement.status)}
            </div>
          </CardHeader>
          <CardContent className="grid gap-4 md:grid-cols-2">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Code</p>
              <p>{entitlement.code || "—"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Status</p>
              <p>{entitlement.status || "—"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Account</p>
              <p>{entitlement.account_id ? <Link href={`/accounts/${entitlement.account_id}`} className="text-primary hover:underline">View account</Link> : "—"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Contact</p>
              <p>{entitlement.contact_id ? <Link href={`/contacts/${entitlement.contact_id}`} className="text-primary hover:underline">View contact</Link> : "—"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Offering</p>
              <p>{entitlement.offering_id ? <Link href="/offerings" className="text-primary hover:underline">View offering catalog</Link> : "—"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Source Deal</p>
              <p>{entitlement.deal_id ? <Link href={`/deals/${entitlement.deal_id}`} className="text-primary hover:underline">Open deal</Link> : "Provisioned from a closed won deal"}</p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Entitlement details</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 md:grid-cols-2">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Quantity</p>
              <p>{entitlement.quantity ?? "—"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Agreed price</p>
              <p>{entitlement.agreed_price ? `${entitlement.currency_code || "USD"} ${entitlement.agreed_price}` : "—"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Start date</p>
              <p>{entitlement.start_date ? new Date(entitlement.start_date).toLocaleDateString() : "—"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">End date</p>
              <p>{entitlement.end_date ? new Date(entitlement.end_date).toLocaleDateString() : "—"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Renewable</p>
              <p>{entitlement.renewable ? "Yes" : "No"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Auto renew</p>
              <p>{entitlement.auto_renew ? "Yes" : "No"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Renewal notice days</p>
              <p>{entitlement.renewal_notice_days ?? "—"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Renewal due date</p>
              <p>{entitlement.renewal_due_date ? new Date(entitlement.renewal_due_date).toLocaleDateString() : "—"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Owner</p>
              <p>{entitlement.owner_user_id || "Unassigned"}</p>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <CalendarRange className="h-5 w-5" />
              Expiry visibility
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-start gap-2 rounded-lg border border-dashed p-3">
              <CircleAlert className="mt-0.5 h-4 w-4 text-muted-foreground" />
              <div>
                <p className="font-medium">{expiry.label}</p>
                <p className="text-sm text-muted-foreground">This label is calculated in the UI and is not persisted.</p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Lifecycle</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-2 text-sm text-muted-foreground">
              <p>Created from Deal</p>
              <p>{entitlement.deal_id ? <Link href={`/deals/${entitlement.deal_id}`} className="text-primary hover:underline">Open the originating deal</Link> : "The deal details are not available."}</p>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
