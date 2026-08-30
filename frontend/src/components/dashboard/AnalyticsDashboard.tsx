"use client";

import { useMemo, useState, type ReactNode } from "react";
import {
  AlertTriangle,
  Briefcase,
  Calendar,
  CheckSquare,
  Clock,
  Inbox,
  Loader2,
  Phone,
  RefreshCw,
  ShieldAlert,
  Target,
  TrendingUp,
  UserCheck,
  Users,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { apiErrorMessage } from "@/lib/api/api-utils";
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

const SCOPE_BADGE_LABELS: Record<AnalyticsScope, string> = {
  PLATFORM: "Platform",
  RESELLER: "Reseller",
  TENANT: "Tenant",
  TEAM: "Team",
  USER: "My activity",
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

function isPermissionDenied(error: unknown): boolean {
  const err = error as {
    response?: { status?: number; data?: { error?: { code?: string } } };
  };
  const code = err?.response?.data?.error?.code;
  return err?.response?.status === 403 || code === "FORBIDDEN" || code === "PERMISSION_DENIED" || code === "ACCESS_DENIED";
}

function getErrorMessage(error: unknown): string {
  return apiErrorMessage(error, "Unable to load analytics data.");
}

function RangePresets({ value, onChange }: { value: RangePreset; onChange: (v: RangePreset) => void }) {
  return (
    <div role="group" aria-label="Date range" className="flex items-center gap-1 rounded-lg border bg-muted p-1">
      {RANGE_PRESETS.map((preset) => (
        <button
          key={preset.value}
          type="button"
          aria-pressed={value === preset.value}
          onClick={() => onChange(preset.value)}
          className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
            value === preset.value
              ? "bg-background text-foreground shadow-sm"
              : "text-muted-foreground hover:text-foreground"
          }`}
        >
          {preset.label}
        </button>
      ))}
    </div>
  );
}

interface KpiCardProps {
  label: string;
  value: number;
  icon: ReactNode;
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
        <p
          className="mt-3 text-2xl font-bold text-foreground tabular-nums sm:text-3xl"
          aria-label={`${label}: ${value.toLocaleString()}`}
        >
          {value.toLocaleString()}
        </p>
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
      <span className={`text-sm font-medium tabular-nums ${tones[tone ?? "default"]}`}>{value}</span>
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

function DashboardSkeleton() {
  return (
    <>
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
    </>
  );
}

interface AnalyticsDashboardProps {
  tenantId?: string;
  tenantName?: string;
  actions?: ReactNode;
}

export function AnalyticsDashboard({ tenantId, tenantName, actions }: AnalyticsDashboardProps) {
  const [selectedPreset, setSelectedPreset] = useState<RangePreset>("30d");

  const dateRange = useMemo(() => getPresetRange(selectedPreset), [selectedPreset]);

  // Permission gate (UX only; the backend remains authoritative). Applied via
  // `enabled` so no analytics request fires without report:read.
  const { hasPermission } = usePermissions();
  const userRole = useAuthStore((s) => s.userRole);
  const canView = userRole === "SUPERADMIN" || userRole === "RESELLER" || hasPermission("report", "read");

  const {
    data,
    isPending,
    isError,
    error,
    refetch,
    isFetching,
  } = useAnalyticsSummary(dateRange, tenantId, { enabled: canView });
  const {
    data: trendData,
    isError: trendsError,
    error: trendsQueryError,
    refetch: refetchTrends,
  } = useAnalyticsTrends(dateRange, tenantId, { enabled: canView });

  if (!canView) {
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

  const scope = data?.scope;
  const title = tenantName ?? (scope ? SCOPE_LABELS[scope] : "Analytics");
  const scopeBadge = scope ? SCOPE_BADGE_LABELS[scope] : null;

  let subtitle = "Aggregate metrics for your visibility scope.";
  if (tenantName) subtitle = `Analytics scoped to ${tenantName}.`;
  else if (scope === "PLATFORM") subtitle = "Platform-wide aggregates across all tenants.";
  else if (scope === "RESELLER") subtitle = "Aggregated metrics across all tenants you manage.";

  const totalCount = data ? data.leads + data.contacts + data.deals + data.tasks + data.calls + data.meetings : 0;
  const isEmpty = Boolean(data) && totalCount === 0;

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
      <header className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
            {scopeBadge && (
              <Badge variant="outline" className="text-xs font-medium text-muted-foreground">
                {scopeBadge}
              </Badge>
            )}
          </div>
          <p className="mt-1 text-sm text-muted-foreground">
            {subtitle}
            {isFetching && (
              <Loader2 className="ml-2 inline h-3 w-3 animate-spin align-[-1px] text-muted-foreground" />
            )}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {actions}
          <RangePresets value={selectedPreset} onChange={setSelectedPreset} />
        </div>
      </header>

      {isPending ? (
        <DashboardSkeleton />
      ) : isError ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed px-4 py-16 space-y-4 text-center">
          {isPermissionDenied(error) ? (
            <>
              <ShieldAlert className="h-8 w-8 text-destructive" />
              <div>
                <p className="text-sm font-medium">You don&apos;t have permission to view these analytics.</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  Your role&apos;s access scope may not cover this view. Contact your administrator.
                </p>
              </div>
            </>
          ) : (
            <>
              <AlertTriangle className="h-8 w-8 text-destructive" />
              <p className="text-sm text-destructive">{getErrorMessage(error)}</p>
            </>
          )}
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RefreshCw className="mr-2 h-4 w-4" />
            Retry
          </Button>
        </div>
      ) : isEmpty ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed px-4 py-16 space-y-4 text-center">
          <Inbox className="h-10 w-10 text-muted-foreground/60" />
          <div>
            <h2 className="text-base font-semibold">No activity in this period</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {tenantName
                ? `No leads, deals, or activities were recorded for ${tenantName} in the selected date range. Try widening the range above.`
                : "No leads, deals, or activities were recorded in the selected date range. Try widening the range above."}
            </p>
          </div>
        </div>
      ) : (
        data && (
          <>
            {/* AN-2: Basic entity counts */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {kpis.map((kpi) => (
                <KpiCard key={kpi.label} label={kpi.label} value={kpi.value} icon={kpi.icon} />
              ))}
            </div>

            {/* AN-3: Expanded metrics */}
            {data.leadMetrics && data.dealMetrics && data.activityMetrics && (
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <LeadMetricsCard data={data.leadMetrics} />
                <DealMetricsCard data={data.dealMetrics} />
                <ActivityMetricsCard data={data.activityMetrics} />
              </div>
            )}

            {/* AN-4: Trend chart */}
            {trendsError ? (
              <Card className="shadow-sm border border-muted">
                <CardContent className="flex items-center justify-between gap-4 py-6">
                  <p className="text-sm text-destructive">
                    {getErrorMessage(trendsQueryError)}
                  </p>
                  <Button variant="outline" size="sm" onClick={() => refetchTrends()}>
                    <RefreshCw className="mr-2 h-4 w-4" />
                    Retry
                  </Button>
                </CardContent>
              </Card>
            ) : (
              <AnalyticsTrendChart data={trendData ?? []} />
            )}

            <p className="text-xs text-muted-foreground text-center">
              Showing data from {new Date(data.from).toLocaleDateString()} to {new Date(data.to).toLocaleDateString()}
            </p>
          </>
        )
      )}
    </div>
  );
}