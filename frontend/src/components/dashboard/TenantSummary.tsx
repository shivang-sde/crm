"use client";

import { useMemo } from "react";
import {
  Loader2,
  RefreshCw,
  Store,
  Building2,
  Building,
  XCircle,
  Users,
  Timer,
  CalendarX,
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { useTenants } from "@/lib/hooks/tenants";

function dateStr(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

interface StatCardProps {
  label: string;
  value: number;
  icon: React.ReactNode;
  hint?: string;
  tone?: "default" | "positive" | "negative" | "warning";
}

function StatCard({ label, value, icon, hint, tone = "default" }: StatCardProps) {
  const tones: Record<string, string> = {
    default: "",
    positive: "text-emerald-600",
    negative: "text-rose-600",
    warning: "text-amber-600",
  };
  return (
    <Card className="shadow-sm border border-muted">
      <CardContent className="pt-4">
        <div className="flex items-center gap-3">
          <span className="inline-flex h-9 w-9 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
            {icon}
          </span>
          <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
            {label}
          </p>
        </div>
        <p className={`mt-3 text-3xl font-bold text-foreground ${tones[tone]}`}>
          {value.toLocaleString()}
        </p>
        {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  );
}

function StatCardSkeleton() {
  return (
    <Card className="shadow-sm border border-muted">
      <CardContent className="pt-4">
        <div className="flex items-center gap-3">
          <Skeleton className="h-9 w-9 rounded-lg" />
          <Skeleton className="h-3 w-24" />
        </div>
        <Skeleton className="mt-3 h-8 w-16" />
      </CardContent>
    </Card>
  );
}

export function TenantSummary({ showResellers = false }: { showResellers?: boolean }) {
  const { data, isLoading, isError, error, refetch, isFetching } = useTenants();

  const metrics = useMemo(() => {
    const tenants = data ?? [];
    const now = new Date();
    const today = dateStr(now);
    const in30 = dateStr(new Date(now.getTime() + 30 * 86_400_000));

    const resellers = new Set<string>();
    let users = 0;
    let active = 0;
    let expiringSoon = 0;
    let expired = 0;

    for (const t of tenants) {
      if (t.reseller?.id) resellers.add(t.reseller.id);
      users += t.currentUsers ?? 0;
      if (t.isActive) active += 1;
      const end = t.subscriptionEndDate;
      if (end) {
        if (end < today) expired += 1;
        else if (t.isActive && end <= in30) expiringSoon += 1;
      }
    }

    return {
      totalTenants: tenants.length,
      totalUsers: users,
      active,
      inactive: tenants.length - active,
      resellers: resellers.size,
      expiringSoon,
      expired,
    };
  }, [data]);

  const activePct = metrics.totalTenants > 0
    ? `${Math.round((metrics.active / metrics.totalTenants) * 100)}%`
    : "0%";
  const inactivePct = metrics.totalTenants > 0
    ? `${Math.round((metrics.inactive / metrics.totalTenants) * 100)}%`
    : "0%";

  if (isLoading) {
    return (
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {Array.from({ length: showResellers ? 7 : 6 }).map((_, i) => (
          <StatCardSkeleton key={i} />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex items-center justify-between rounded-lg border border-dashed px-4 py-3">
        <p className="text-sm text-destructive">
          {error instanceof Error ? error.message : "Unable to load tenant data."}
        </p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          <RefreshCw className="mr-2 h-4 w-4" />
          Retry
        </Button>
      </div>
    );
  }

  if (metrics.totalTenants === 0) {
    return (
      <Card className="shadow-sm border border-muted">
        <CardContent className="py-10 text-center text-sm text-muted-foreground">
          No tenants under this account yet.
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {showResellers && (
          <StatCard
            label="Resellers"
            value={metrics.resellers}
            icon={<Store className="h-5 w-5" />}
            hint={`${metrics.resellers > 0 && metrics.totalTenants > 0 ? Math.round((metrics.resellers / metrics.totalTenants) * 100) : 0}% of tenants belong to a reseller`}
          />
        )}
        <StatCard
          label="Tenants"
          value={metrics.totalTenants}
          icon={<Building2 className="h-5 w-5" />}
          hint={`${metrics.active.toLocaleString()} active / ${metrics.inactive.toLocaleString()} inactive`}
        />
        <StatCard
          label="Active Tenants"
          value={metrics.active}
          icon={<Building className="h-5 w-5" />}
          tone="positive"
          hint={`${activePct} of tenants`}
        />
        <StatCard
          label="Inactive Tenants"
          value={metrics.inactive}
          icon={<XCircle className="h-5 w-5" />}
          tone={metrics.inactive > 0 ? "negative" : "default"}
          hint={`${inactivePct} of tenants`}
        />
        <StatCard
          label="Total Users"
          value={metrics.totalUsers}
          icon={<Users className="h-5 w-5" />}
          hint="tenant seats currently in use"
        />
        <StatCard
          label="Expiring Soon"
          value={metrics.expiringSoon}
          icon={<Timer className="h-5 w-5" />}
          tone={metrics.expiringSoon > 0 ? "warning" : "positive"}
          hint="subscription ends within 30 days"
        />
        <StatCard
          label="Expired"
          value={metrics.expired}
          icon={<CalendarX className="h-5 w-5" />}
          tone={metrics.expired > 0 ? "negative" : "default"}
          hint="subscription already ended"
        />
      </div>
      {isFetching && <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />}
    </div>
  );
}