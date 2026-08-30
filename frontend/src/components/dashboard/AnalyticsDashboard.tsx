"use client";

import { useMemo, useState } from "react";
import { Loader2, RefreshCw, Users, UserCheck, Briefcase, CheckSquare, Phone, Calendar, TrendingUp, Target, Clock, ShieldAlert } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { useAnalyticsSummary, useAnalyticsTrends } from "@/lib/hooks/analytics";
import { useAuthStore } from "@/lib/store/authStore";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { AnalyticsTrendChart } from "./AnalyticsTrendChart";
import type { AnalyticsScope, LeadMetrics, DealMetrics, ActivityMetrics } from "@/types/analytics";

const SCOPE_LABELS: Record<AnalyticsScope, string> = {
  PLATFORM: "Platform Overview",
  RESELLER: "Reseller Overview",
  TENANT: "Company Overview",
  TEAM: "Team Overview",
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

function formatMoney(value: number | null | undefined): string {
  if (value === null || value === undefined) return "\u2014";
  return value.toLocaleString(undefined, { maximumFractionDigits: 0 });
}

function formatRate(value: number): string {
  return `${value.toFixed(1)}%`;
}

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

function MetricRow({ label, value, tone }: { label: string; value: string; tone?: "default" | "positive" | "negative" | "warning" }) {
  const tones: Record<string, string> = {
    default: "",
    positive: "text-emerald-600",
    negative: "text-rose-600",
    warning: "text-amber-600",
  };
  return (
    <div className="flex items-center justify-between py-1.5">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className={`text-sm font-medium ${tones[tone ?? "default"]}`}>{value}</span>
    </div>
  );
}

function MetricSectionSkeleton() {
  return (
    <Card className="shadow-sm border border-muted">
      <CardHeader className="pb-2">
        <Skeleton className="h-5 w-32" />
      </CardHeader>
      <CardContent>
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex items-center justify-between py-1.5">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-4 w-12" />
          </div>
        ))}
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

function LeadMetricsCard({ data }: { data: LeadMetrics }) {
  return (
    <Card className="shadow-sm border border-muted">
      <CardHeader className="pb-2">
        <CardTitle className="text-base font-semibold flex items-center gap-2">
          <Target className="h-4 w-4 text-blue-600" />
          Lead Performance
        </CardTitle>
      </CardHeader>
      <CardContent>
        <MetricRow label="New leads" value={data.newLeads.toLocaleString()} />
        <MetricRow label="Converted" value={data.convertedLeads.toLocaleString()} tone="positive" />
        <MetricRow label="Conversion rate" value={formatRate(data.conversionRate)} tone={data.conversionRate > 0 ? "positive" : "default"} />
      </CardContent>
    </Card>
  );
}

function DealMetricsCard({ data }: { data: DealMetrics }) {
  return (
    <Card className="shadow-sm border border-muted">
      <CardHeader className="pb-2">
        <CardTitle className="text-base font-semibold flex items-center gap-2">
          <TrendingUp className="h-4 w-4 text-emerald-600" />
          Deal Performance
        </CardTitle>
      </CardHeader>
      <CardContent>
        <MetricRow label="Open deals" value={data.openDeals.toLocaleString()} />
        <MetricRow label="Won deals" value={data.wonDeals.toLocaleString()} tone="positive" />
        <MetricRow label="Lost deals" value={data.lostDeals.toLocaleString()} tone={data.lostDeals > 0 ? "negative" : "default"} />
        <MetricRow label="Pipeline value" value={formatMoney(data.pipelineValue)} />
        <MetricRow label="Won value" value={formatMoney(data.wonValue)} tone="positive" />
        <MetricRow label="Win rate" value={formatRate(data.winRate)} tone={data.winRate > 0 ? "positive" : "default"} />
      </CardContent>
    </Card>
  );
}

function ActivityMetricsCard({ data }: { data: ActivityMetrics }) {
  return (
    <Card className="shadow-sm border border-muted">
      <CardHeader className="pb-2">
        <CardTitle className="text-base font-semibold flex items-center gap-2">
          <Clock className="h-4 w-4 text-amber-600" />
          Task &amp; Activity
        </CardTitle>
      </CardHeader>
      <CardContent>
        <MetricRow label="Open tasks" value={data.openTasks.toLocaleString()} />
        <MetricRow label="Completed" value={data.completedTasks.toLocaleString()} tone="positive" />
        <MetricRow label="Overdue" value={data.overdueTasks.toLocaleString()} tone={data.overdueTasks > 0 ? "negative" : "default"} />
      </CardContent>
    </Card>
  );
}

export function AnalyticsDashboard() {
  const [selectedPreset, setSelectedPreset] = useState<RangePreset>("30d");

  const dateRange = useMemo(() => getPresetRange(selectedPreset), [selectedPreset]);

  const { data, isLoading, isError, error, refetch, isFetching } = useAnalyticsSummary(dateRange);
  const { data: trendData } = useAnalyticsTrends(dateRange);

  // Permission gate (UX only; the backend remains authoritative). The scope is
  // always read from the backend response, never derived from role names.
  const { hasPermission } = usePermissions();
  const userRole = useAuthStore((s) => s.userRole);
  const isPlatformRole = userRole === "SUPERADMIN" || userRole === "RESELLER";

  if (!isPlatformRole && !hasPermission("report", "read")) {
    return (
      <div className="flex flex-col items-center justify-center py-20 space-y-4 text-center">
        <ShieldAlert className="h-10 w-10 text-muted-foreground" />
        <h1 className="text-2xl font-bold tracking-tight">Analytics Dashboard</h1>
        <p className="text-sm text-muted-foreground">
          You don&apos;t have permission to view analytics.
        </p>
      </div>
    );
  }

  const scopeLabel = data?.scope ? SCOPE_LABELS[data.scope] : "";

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
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <MetricSectionSkeleton />
          <MetricSectionSkeleton />
          <MetricSectionSkeleton />
        </div>
        <Card className="shadow-sm border border-muted">
          <CardHeader className="pb-2">
            <Skeleton className="h-5 w-40" />
          </CardHeader>
          <CardContent>
            <Skeleton className="h-[320px] w-full" />
          </CardContent>
        </Card>
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

      {/* AN-2: Basic entity counts */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {kpis.map((kpi) => (
          <KpiCard key={kpi.label} label={kpi.label} value={kpi.value} icon={kpi.icon} />
        ))}
      </div>

      {/* AN-3: Expanded metrics */}
      {data && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {data.leadMetrics && <LeadMetricsCard data={data.leadMetrics} />}
          {data.dealMetrics && <DealMetricsCard data={data.dealMetrics} />}
          {data.activityMetrics && <ActivityMetricsCard data={data.activityMetrics} />}
        </div>
      )}

      {/* AN-4: Trend chart */}
      {trendData && trendData.length > 0 && (
        <AnalyticsTrendChart data={trendData} />
      )}

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
