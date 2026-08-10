"use client";

import Link from "next/link";
import { Package2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useEntitlements } from "@/lib/hooks/entitlements";

interface AccountEntitlementsSectionProps {
  accountId: string;
}

export function AccountEntitlementsSection({ accountId }: AccountEntitlementsSectionProps) {
  const { data, isLoading } = useEntitlements({ accountId, size: 5 });
  const entitlements = data?.data ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Package2 className="h-5 w-5" />
          Customer entitlements
        </CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <p className="text-sm text-muted-foreground">Loading entitlements…</p>
        ) : entitlements.length === 0 ? (
          <p className="text-sm text-muted-foreground">No entitlements linked to this account yet.</p>
        ) : (
          <div className="space-y-3">
            {entitlements.map((entitlement) => (
              <div key={entitlement.id} className="rounded-lg border p-3">
                <div className="flex items-center justify-between gap-2">
                  <Link href={`/entitlements/${entitlement.id}`} className="font-medium text-primary hover:underline">
                    {entitlement.name || entitlement.code || "Untitled entitlement"}
                  </Link>
                  <span className="text-xs uppercase tracking-wide text-muted-foreground">{entitlement.status || "UNKNOWN"}</span>
                </div>
                <p className="mt-1 text-sm text-muted-foreground">
                  {entitlement.description || "Provisioned from a closed-won deal."}
                </p>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
