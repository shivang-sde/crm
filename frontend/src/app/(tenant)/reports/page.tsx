"use client";

import { useState } from "react";
import { useAuthStore } from "@/lib/store/authStore";
import { useTenants } from "@/lib/hooks/tenants";
import { TenantSelector } from "@/components/dashboard/TenantSelector";
import { OperationalReports } from "@/components/reports/OperationalReports";

/**
 * Operational reports (/reports).
 *
 * Shared surface: platform roles (SUPERADMIN, RESELLER) get an embedded
 * tenant drill-down; tenant users report on their own tenant.
 */
export default function ReportsPage() {
  const userRole = useAuthStore((s) => s.userRole);
  const isPlatform = userRole === "SUPERADMIN" || userRole === "RESELLER";
  return isPlatform ? <PlatformReportsPage /> : <OperationalReports />;
}

function PlatformReportsPage() {
  const userRole = useAuthStore((s) => s.userRole);
  const { data: tenants = [], isLoading, isError, refetch } = useTenants();
  const [selectedTenantId, setSelectedTenantId] = useState<string | null>(null);

  const selectedTenant = tenants.find((t) => t.id === selectedTenantId);

  return (
    <OperationalReports
      tenantId={selectedTenantId ?? undefined}
      tenantName={selectedTenant?.name || selectedTenant?.slug}
      actions={
        <TenantSelector
          tenants={tenants}
          selectedTenantId={selectedTenantId}
          onSelect={setSelectedTenantId}
          placeholder={userRole === "RESELLER" ? "All My Tenants" : "All Tenants"}
          isLoading={isLoading}
          isError={isError}
          onRetry={refetch}
        />
      }
    />
  );
}