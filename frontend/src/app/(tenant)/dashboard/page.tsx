import { SalesDashboard } from "@/components/dashboard/SalesDashboard";

export default function TenantDashboard() {
  return (
    <div className="p-6 space-y-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Sales Dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Pipeline, forecast and funnel metrics scoped to your visibility.
        </p>
      </div>
      <SalesDashboard />
    </div>
  );
}
