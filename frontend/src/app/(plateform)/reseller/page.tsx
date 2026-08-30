import { AnalyticsDashboard } from "@/components/dashboard/AnalyticsDashboard";
import { TenantSummary } from "@/components/dashboard/TenantSummary";

export default function ResellerPage() {
  return (
    <div className="space-y-8">
      <section aria-label="Administrative health">
        <h2 className="text-lg font-semibold tracking-tight">Administrative Health</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Subscription status of the tenants you manage.
        </p>
        <TenantSummary />
      </section>

      <section aria-label="CRM overview">
        <h2 className="text-lg font-semibold tracking-tight mb-4">CRM Overview</h2>
        <AnalyticsDashboard />
      </section>
    </div>
  );
}