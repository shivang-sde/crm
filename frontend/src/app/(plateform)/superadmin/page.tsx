import { AnalyticsDashboard } from "@/components/dashboard/AnalyticsDashboard";
import { TenantSummary } from "@/components/dashboard/TenantSummary";

export default function SuperAdminPage() {
  return (
    <div className="space-y-8">
      <section aria-label="Administrative health">
        <h2 className="text-lg font-semibold tracking-tight">Administrative Health</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Tenant and subscription status across your platform.
        </p>
        <TenantSummary showResellers />
      </section>

      <section aria-label="CRM overview">
        <h2 className="text-lg font-semibold tracking-tight mb-4">CRM Overview</h2>
        <AnalyticsDashboard />
      </section>
    </div>
  );
}