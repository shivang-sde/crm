"use client";

import { useState } from "react";
import { AnalyticsDashboard } from "@/components/dashboard/AnalyticsDashboard";
import { TenantSummary } from "@/components/dashboard/TenantSummary";
import { TenantSelector } from "@/components/dashboard/TenantSelector";
import { useTenants } from "@/lib/hooks/tenants";

export default function SuperAdminPage() {
  const { data: tenants, isLoading: tenantsLoading, isError: tenantsError, refetch: refetchTenants } = useTenants();
  const [selectedTenantId, setSelectedTenantId] = useState<string | null>(null);

  const tenantOptions = tenants ?? [];
  const selectedTenant = tenantOptions.find((t) => t.id === selectedTenantId);

  return (
    <div className="space-y-8">
      <section aria-label="Administrative health">
        <div className="mb-4">
          <h2 className="text-lg font-semibold tracking-tight">Administrative Health</h2>
          <p className="text-sm text-muted-foreground">
            Tenant and subscription status across your platform.
          </p>
        </div>
        <TenantSummary showResellers />
      </section>

      <section aria-label="CRM analytics">
        <AnalyticsDashboard
          tenantId={selectedTenantId ?? undefined}
          tenantName={selectedTenant?.name || selectedTenant?.slug}
          actions={
            <TenantSelector
              tenants={tenantOptions}
              selectedTenantId={selectedTenantId}
              onSelect={setSelectedTenantId}
              placeholder="All Tenants"
              isLoading={tenantsLoading}
              isError={tenantsError}
              onRetry={refetchTenants}
            />
          }
        />
      </section>
    </div>
  );
}