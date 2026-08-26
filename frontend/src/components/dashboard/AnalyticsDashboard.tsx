"use client";

import { useMemo, useState } from "react";
import { Loader2, RefreshCw, Users, UserCheck, Briefcase, CheckSquare, Phone, Calendar } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { useAnalyticsSummary } from "@/lib/hooks/analytics";
import { useAuthStore } from "@/lib/store/authStore";
import type { AnalyticsScope } from "@/types/analytics";

const SCOPE_LABELS: Record<AnalyticsScope, string> = {
  PLATFORM: "Platform Overview",
  RESELLER: "Reseller Overview",
  TENANT: "Company Overview",
  USER: "My Dashboard",
};

type RangePreset = "7d" | "30d" | "90d";

function getPresetRange(preset: RangePreset): { from: string; to: string } {
  const to = new Date().toISOString();
  const days = preset === "7d" ? 7 : preset === "30d" ? 30 : 90;
  const from = new Date(Date.now() - days * 86_400_000).toISOString();
  return { from, to };
}

const RANGE_PRESETS: { label: string; value: RangePreset }[] = [
  { label: "Last 7 days", value: "7d" },
  { label: "Last 30 days", value: "30d" },
  { label: "Last 90 days", value: "90d" },
];

interface KpiCardProps {
  label: string;
  value: number;
  icon: React.ReactNode;
}

function KpiCard({ label, value, icon }: KpiCardProps) {
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
        <p className="mt-3 text-3xl font-bold text-foreground">{value.toLocaleString()}</p>
      </CardContent>
    </Card>
  );
}

function KpiCardSkeleton() {
  return (
    <Card className="shadow-sm border border-muted">
      <CardContent className="pt-4">
        <div className="flex items-center gap-3">
          <Skeleton className="h-9 w-9 rounded-lg" />
          <Skeleton className="h-3 w-20" />
        </div>
        <Skeleton className="mt-3 h-8 w-16" />
      </CardContent>
    </Card>
  );
}

export function AnalyticsDashboard() {
  const [selectedPreset, setSelectedPreset] = useState<RangePreset>("30d");

  const dateRange = useMemo(() => getPresetRange(selectedPreset), [selectedPreset]);

  const { data, isLoading, isError, error, refetch, isFetching } = useAnalyticsSummary(dateRange);

  const userRole = useAuthStore((s) => s.userRole);
  const scopeLabel = data?.scope
    ? SCOPE_LABELS[data.scope]
    : userRole === "SUPERADMIN"
      ? SCOPE_LABELS.PLATFORM
      : userRole === "RESELLER"
        ? SCOPE_LABELS.RESELLER
        : userRole === "ADMIN"
          ? SCOPE_LABELS.TENANT
          : SCOPE_LABELS.USER;

  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div className="space-y-1">
            <Skeleton className="h-7 w-48" />
            <Skeleton className="h-4 w-64" />
          </div>
          <Skeleton className="h-9 w-36" />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <KpiCardSkeleton key={i} />
          ))}
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-20 space-y-4">
        <p className="text-sm text-destructive">
          {error instanceof Error ? error.message : "Unable to load analytics data."}
        </p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          <RefreshCw className="mr-2 h-4 w-4" />
          Retry
        </Button>
      </div>
    );
  }

  const kpis = data
    ? [
        { label: "Leads", value: data.leads, icon: <Users className="h-5 w-5" /> },
        { label: "Contacts", value: data.contacts, icon: <UserCheck className="h-5 w-5" /> },
        { label: "Deals", value: data.deals, icon: <Briefcase className="h-5 w-5" /> },
        { label: "Tasks", value: data.tasks, icon: <CheckSquare className="h-5 w-5" /> },
        { label: "Calls", value: data.calls, icon: <Phone className="h-5 w-5" /> },
        { label: "Meetings", value: data.meetings, icon: <Calendar className="h-5 w-5" /> },
      ]
    : [];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">{scopeLabel}</h1>
          <p className="text-sm text-muted-foreground">
            Aggregate metrics for your visibility scope.
            {isFetching && (
              <Loader2 className="inline ml-2 h-3 w-3 animate-spin text-muted-foreground" />
            )}
          </p>
        </div>
        <div className="flex items-center gap-1 rounded-lg border bg-muted p-1">
          {RANGE_PRESETS.map((preset) => (
            <button
              key={preset.value}
              onClick={() => setSelectedPreset(preset.value)}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                selectedPreset === preset.value
                  ? "bg-background text-foreground shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              {preset.label}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {kpis.map((kpi) => (
          <KpiCard key={kpi.label} label={kpi.label} value={kpi.value} icon={kpi.icon} />
        ))}
      </div>

      {!isFetching && data && (
        <p className="text-xs text-muted-foreground text-center">
          Showing data from{" "}
          {new Date(data.from).toLocaleDateString()} to{" "}
          {new Date(data.to).toLocaleDateString()}
        </p>
      )}
    </div>
  );
}
