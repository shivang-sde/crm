"use client";

import { useState } from "react";
import { AnalyticsDashboard } from "@/components/dashboard/AnalyticsDashboard";
import { TenantSummary } from "@/components/dashboard/TenantSummary";
import { TenantSelector } from "@/components/dashboard/TenantSelector";
import { useTenants } from "@/lib/hooks/tenants";

export default function ResellerPage() {
  const { data: tenants, isLoading: tenantsLoading } = useTenants();
  const [selectedTenantId, setSelectedTenantId] = useState<string | null>(null);

  const handleTenantSelect = (tenantId: string | null) => {
    setSelectedTenantId(tenantId);
  };

  const tenantOptions = tenants ?? [];

  return (
    <div className="space-y-8">
      <section aria-label="Administrative health">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-4">
          <div>
            <h2 className="text-lg font-semibold tracking-tight">Administrative Health</h2>
            <p className="text-sm text-muted-foreground">
              Subscription status of the tenants you manage.
            </p>
          </div>
          <div className="w-full sm:w-auto">
            <TenantSelector
              tenants={tenantOptions}
              selectedTenantId={selectedTenantId}
              onSelect={handleTenantSelect}
              placeholder="All My Tenants"
              isLoading={tenantsLoading}
            />
          </div>
        </div>
        <TenantSummary />
      </section>

      <section aria-label="CRM overview">
        <div className="flex flex-col sm:flex-row sm:items_center sm:justify-between gap-4 mb-4">
          <div>
            <h2 className="text-lg font-semibold tracking-tight">CRM Overview</h2>
            <p className="text-sm text-muted-foreground">
              {selectedTenantId
                ? "Analytics for selected tenant."
                : "Reseller-wide aggregate metrics."}
            </p>
          </div>
        </div>
        <AnalyticsDashboard tenantId={selectedTenantId ?? undefined} />
      </section>
    </div>
  );
}