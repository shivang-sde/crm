"use client";

import { useMemo, useState, type ReactNode } from "react";
import {
  AlertTriangle,
  BarChart3,
  Building2,
  Clock,
  Contact,
  Download,
  Hourglass,
  Inbox,
  Layers,
  ListChecks,
  Loader2,
  PhoneCall,
  RefreshCw,
  ShieldAlert,
  Tag,
  Target,
  Timer,
  TrendingUp,
  UserRound,
} from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { analyticsApi } from "@/lib/api/analytics";
import { apiErrorMessage } from "@/lib/api/api-utils";
import {
  useAnalyticsSummary,
  usePipelineByStage,
  usePipelineByOwner,
  usePipelineByAccount,
  useConversionByOwner,
  useDealAging,
  useCallStatus,
  useConversionDuringPeriod,
  usePipelineByForecastCategory,
  useCurrentStageAge,
  useActivityRates,
  useCallDuration,
  useContactsPerAccount,
  useLeadSourcePerformance,
  useAccountsByOwner,
} from "@/lib/hooks/analytics";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { useAuthStore } from "@/lib/store/authStore";
import { csvExportFileName, downloadBlob } from "@/lib/utils";
import type {
  ActivityMetrics,
  AnalyticsScope,
  DealMetrics,
  GroupedDataset,
  LeadMetrics,
} from "@/types/analytics";

const SCOPE_BADGE_LABELS: Record<AnalyticsScope, string> = {
  PLATFORM: "Platform",
  RESELLER: "Reseller",
  TENANT: "Tenant",
  TEAM: "Team",
  USER: "My activity",
};

type RangePreset = "7d" | "30d" | "90d" | "365d";

function getPresetRange(preset: RangePreset): { from: string; to: string } {
  const to = new Date().toISOString();
  const days = preset === "7d" ? 7 : preset === "30d" ? 30 : preset === "90d" ? 90 : 365;
  const from = new Date(Date.now() - days * 86_400_000).toISOString();
  return { from, to };
}

const RANGE_PRESETS: { label: string; value: RangePreset }[] = [
  { label: "Last 7 days", value: "7d" },
  { label: "Last 30 days", value: "30d" },
  { label: "Last 90 days", value: "90d" },
  { label: "Last 365 days", value: "365d" },
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
  return apiErrorMessage(error, "Unable to load reports.");
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

interface ReportRow {
  id: string;
  label: string;
  value: string;
  hint?: string;
  tone?: "positive" | "negative" | "default";
}

const TONE_CLASS: Record<NonNullable<ReportRow["tone"]>, string> = {
  default: "text-foreground",
  positive: "text-emerald-600",
  negative: "text-rose-600",
};

function ReportTable({ caption, rows }: { caption: string; rows: ReportRow[] }) {
  return (
    <Table>
      <TableCaption className="sr-only">{caption}</TableCaption>
      <TableHeader>
        <TableRow>
          <TableHead>Metric</TableHead>
          <TableHead className="text-right">Value</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.id}>
            <TableCell title={row.hint} className="text-sm text-muted-foreground">
              {row.label}
            </TableCell>
            <TableCell
              className={`text-right text-sm font-medium tabular-nums ${TONE_CLASS[row.tone ?? "default"]}`}
            >
              {row.value}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function leadConversionRows(m: LeadMetrics, convertedDuringPeriod?: number): ReportRow[] {
  const rows: ReportRow[] = [
    { id: "new", label: "New leads", value: m.newLeads.toLocaleString(), hint: "Leads created in the selected period." },
    {
      id: "converted",
      label: "Converted leads (of new leads)",
      value: m.convertedLeads.toLocaleString(),
      hint: "New leads that have since converted to deals.",
      tone: "positive",
    },
    {
      id: "rate",
      label: "New-Lead conversion rate",
      value: formatRate(m.conversionRate),
      hint: "Converted / new leads - both refer to leads created in the selected period.",
      tone: "positive",
    },
  ];
  if (convertedDuringPeriod !== undefined) {
    rows.push({
      id: "converted-period",
      label: "Converted during period",
      value: convertedDuringPeriod.toLocaleString(),
      hint: "Leads whose conversion date fell in the selected period (conversion-event basis, distinct from the new-lead cohort above).",
      tone: "positive",
    });
  }
  return rows;
}

function salesPipelineRows(m: DealMetrics): ReportRow[] {
  return [
    { id: "open", label: "Open deals", value: m.openDeals.toLocaleString(), hint: "Deals created in the selected period whose current stage is open." },
    { id: "won", label: "Won deals", value: m.wonDeals.toLocaleString(), hint: "Deals created in the selected period whose current stage is won.", tone: "positive" },
    { id: "lost", label: "Lost deals", value: m.lostDeals.toLocaleString(), hint: "Deals created in the selected period whose current stage is lost.", tone: m.lostDeals > 0 ? "negative" : "default" },
    { id: "pipeline-value", label: "Pipeline value", value: formatMoney(m.pipelineValue), hint: "Current-stage value of open deals created in the selected period (not historic period-ending values)." },
    { id: "won-value", label: "Won value", value: formatMoney(m.wonValue), hint: "Current-stage value of won deals created in the selected period.", tone: "positive" },
    { id: "win-rate", label: "Win rate", value: formatRate(m.winRate), tone: m.winRate > 0 ? "positive" : "default" },
  ];
}

function activityRows(m: ActivityMetrics, summary: { calls: number; meetings: number }): ReportRow[] {
  return [
    { id: "open", label: "Open tasks", value: m.openTasks.toLocaleString(), hint: "Tasks created in the selected period that are not yet complete." },
    { id: "completed", label: "Created & completed", value: m.completedTasks.toLocaleString(), hint: "Tasks created and completed within the selected period.", tone: "positive" },
    { id: "overdue", label: "Overdue", value: m.overdueTasks.toLocaleString(), hint: "Tasks with a due date before the end of the selected period that are not yet complete.", tone: m.overdueTasks > 0 ? "negative" : "default" },
    { id: "calls", label: "Calls", value: summary.calls.toLocaleString(), hint: "Calls created in the selected period." },
    { id: "meetings", label: "Meetings", value: summary.meetings.toLocaleString(), hint: "Meetings created in the selected period." },
  ];
}

interface MetricTableCardProps {
  icon: ReactNode;
  title: string;
  description: string;
  caption: string;
  rows: ReportRow[];
}

function MetricTableCard({ icon, title, description, caption, rows }: MetricTableCardProps) {
  return (
    <Card className="shadow-sm border border-muted">
      <CardHeader className="pb-2">
        <CardTitle className="text-base font-semibold flex items-center gap-2">{icon}{title}</CardTitle>
        <p className="text-xs text-muted-foreground leading-relaxed">{description}</p>
      </CardHeader>
      <CardContent>
        <ReportTable caption={caption} rows={rows} />
      </CardContent>
    </Card>
  );
}

function GroupedTable({
  caption,
  headers,
  rows,
}: {
  caption: string;
  headers: string[];
  rows: (string | number | null)[][];
}) {
  return (
    <Table>
      <TableCaption className="sr-only">{caption}</TableCaption>
      <TableHeader>
        <TableRow>
          {headers.map((h, i) => (
            <TableHead key={`${h}-${i}`} className={i === 0 ? "" : "text-right"}>{h}</TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((cells, i) => (
          <TableRow key={i}>
            {cells.map((c, j) => (
              <TableCell
                key={j}
                className={j === 0 ? "text-sm font-medium" : "text-right text-sm tabular-nums"}
                title={j === 0 && typeof c === "string" ? c : undefined}
              >
                {c === null || c === undefined
                  ? "\u2014"
                  : typeof c === "number"
                    ? c.toLocaleString()
                    : c}
              </TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

interface GroupedCardProps {
  icon: ReactNode;
  title: string;
  description: string;
  canExport: boolean;
  exporting: boolean;
  onExport: () => void;
  isPending: boolean;
  isError: boolean;
  error: unknown;
  isEmpty: boolean;
  onRetry: () => void;
  children: ReactNode;
}

function GroupedCard({
  icon,
  title,
  description,
  canExport,
  exporting,
  onExport,
  isPending,
  isError,
  error,
  isEmpty,
  onRetry,
  children,
}: GroupedCardProps) {
  return (
    <Card className="shadow-sm border border-muted">
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between gap-2">
          <CardTitle className="text-base font-semibold flex items-center gap-2">{icon}{title}</CardTitle>
          {canExport && (
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8"
              disabled={exporting || isPending || isError || isEmpty}
              onClick={onExport}
              title={`Export ${title} as CSV`}
              aria-label={`Export ${title} as CSV`}
            >
              {exporting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
            </Button>
          )}
        </div>
        <p className="text-xs text-muted-foreground leading-relaxed">{description}</p>
      </CardHeader>
      <CardContent>
        {isPending ? (
          <div className="space-y-3">
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-3/4" />
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center gap-3 py-4 text-center">
            <AlertTriangle className="h-6 w-6 text-destructive" />
            <p className="text-xs text-destructive">{getErrorMessage(error)}</p>
            <Button variant="outline" size="sm" onClick={onRetry}>
              <RefreshCw className="mr-1.5 h-3 w-3" />
              Retry
            </Button>
          </div>
        ) : isEmpty ? (
          <div className="flex flex-col items-center gap-2 py-6 text-center">
            <Inbox className="h-6 w-6 text-muted-foreground/60" />
            <p className="text-xs text-muted-foreground">No data in this period.</p>
          </div>
        ) : (
          children
        )}
      </CardContent>
    </Card>
  );
}

function ReportsSkeleton() {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
      {Array.from({ length: 3 }).map((_, i) => (
        <Card key={i} className="shadow-sm border border-muted">
          <CardHeader className="pb-2">
            <Skeleton className="h-5 w-40" />
            <Skeleton className="h-3 w-full max-w-[240px]" />
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              {Array.from({ length: 6 }).map((__, j) => (
                <Skeleton key={j} className="h-4 w-full" />
              ))}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

interface OperationalReportsProps {
  tenantId?: string;
  tenantName?: string;
  actions?: ReactNode;
}

export function OperationalReports({ tenantId, tenantName, actions }: OperationalReportsProps) {
  const [selectedPreset, setSelectedPreset] = useState<RangePreset>("30d");

  const dateRange = useMemo(() => getPresetRange(selectedPreset), [selectedPreset]);

  // Permission gate (UX only; the backend remains authoritative). Applied via
  // `enabled` so no analytics request fires without report:read.
  const { hasPermission } = usePermissions();
  const userRole = useAuthStore((s) => s.userRole);
  const canView = userRole === "SUPERADMIN" || userRole === "RESELLER" || hasPermission("report", "read");
  const canExport = userRole === "SUPERADMIN" || userRole === "RESELLER" || hasPermission("report", "export");

  const [exporting, setExporting] = useState(false);

  const handleExport = async () => {
    setExporting(true);
    try {
      const blob = await analyticsApi.exportSummary(dateRange, tenantId);
      downloadBlob(blob, csvExportFileName("summary", dateRange.to));
      toast.success("Summary report exported.");
    } catch (err) {
      toast.error(apiErrorMessage(err, "Export failed."));
    } finally {
      setExporting(false);
    }
  };

  const {
    data,
    isPending,
    isError,
    error,
    refetch,
    isFetching,
  } = useAnalyticsSummary(dateRange, tenantId, { enabled: canView });

  const stage = usePipelineByStage(dateRange, tenantId, { enabled: canView });
  const owner = usePipelineByOwner(dateRange, tenantId, { enabled: canView });
  const account = usePipelineByAccount(dateRange, tenantId, { enabled: canView });
  const conversion = useConversionByOwner(dateRange, tenantId, { enabled: canView });
  const aging = useDealAging(dateRange, tenantId, { enabled: canView });
  const calls = useCallStatus(dateRange, tenantId, { enabled: canView });
  const conversionPeriod = useConversionDuringPeriod(dateRange, tenantId, { enabled: canView });
  const forecastCategory = usePipelineByForecastCategory(dateRange, tenantId, { enabled: canView });
  const currentStageAge = useCurrentStageAge(dateRange, tenantId, { enabled: canView });
  const activityRates = useActivityRates(dateRange, tenantId, { enabled: canView });
  const callDuration = useCallDuration(dateRange, tenantId, { enabled: canView });
  const leadSource = useLeadSourcePerformance(dateRange, tenantId, { enabled: canView });
  const contactsAccount = useContactsPerAccount(dateRange, tenantId, { enabled: canView });
  const accountsOwner = useAccountsByOwner(dateRange, tenantId, { enabled: canView });

  const [exportingDataset, setExportingDataset] = useState<GroupedDataset | null>(null);

  const handleGroupedExport = async (dataset: GroupedDataset) => {
    setExportingDataset(dataset);
    try {
      const blob = await analyticsApi.exportGrouped(dataset, dateRange, tenantId);
      downloadBlob(blob, csvExportFileName(dataset, dateRange.to));
      toast.success("Report exported.");
    } catch (err) {
      toast.error(apiErrorMessage(err, "Export failed."));
    } finally {
      setExportingDataset(null);
    }
  };

  if (!canView) {
    return (
      <div className="flex flex-col items-center justify-center py-20 space-y-4 text-center">
        <ShieldAlert className="h-10 w-10 text-muted-foreground" />
        <h1 className="text-2xl font-bold tracking-tight">Operational Reports</h1>
        <p className="text-sm text-muted-foreground">
          You don&apos;t have permission to view reports.
        </p>
      </div>
    );
  }

  const scope = data?.scope;
  const title = tenantName ?? "Operational Reports";
  const scopeBadge = scope ? SCOPE_BADGE_LABELS[scope] : null;

  let subtitle = "Sales pipeline, lead conversion, and activity summaries.";
  if (tenantName) subtitle = `Sales, lead conversion, and activity metrics scoped to ${tenantName}.`;
  else if (scope === "PLATFORM") subtitle = "Platform-wide sales, lead conversion, and activity metrics across all tenants.";
  else if (scope === "RESELLER") subtitle = "Sales, lead conversion, and activity metrics across all tenants you manage.";

  const totalCount = data ? data.leads + data.contacts + data.deals + data.tasks + data.calls + data.meetings : 0;
  const isEmpty = Boolean(data) && totalCount === 0;

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
          {canExport && (
            <Button variant="outline" size="sm" onClick={handleExport} disabled={exporting || isPending || isError}>
              {exporting ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Download className="mr-2 h-4 w-4" />
              )}
              Export summary CSV
            </Button>
          )}
          <RangePresets value={selectedPreset} onChange={setSelectedPreset} />
        </div>
      </header>

      {isPending ? (
        <ReportsSkeleton />
      ) : isError ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed px-4 py-16 space-y-4 text-center">
          {isPermissionDenied(error) ? (
            <>
              <ShieldAlert className="h-8 w-8 text-destructive" />
              <div>
                <p className="text-sm font-medium">You don&apos;t have permission to view these reports.</p>
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
      ) : data ? (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            <MetricTableCard
              icon={<Target className="h-4 w-4 text-blue-600" />}
              title="Lead Conversion"
              description="Leads created in the selected period, how many converted overall, and conversions completed during the period."
              caption="Lead conversion metrics"
              rows={leadConversionRows(data.leadMetrics, conversionPeriod.data?.convertedDuringPeriod)}
            />
            <MetricTableCard
              icon={<TrendingUp className="h-4 w-4 text-emerald-600" />}
              title="Sales Pipeline"
              description="Current stage snapshot of deals created in the selected period."
              caption="Sales pipeline metrics"
              rows={salesPipelineRows(data.dealMetrics)}
            />
            <MetricTableCard
              icon={<Clock className="h-4 w-4 text-amber-600" />}
              title="Activity"
              description="Tasks, calls, and meetings created in the selected period."
              caption="Activity metrics"
              rows={activityRows(data.activityMetrics, data)}
            />
          </div>
          <section className="space-y-4">
            <div>
              <h2 className="text-lg font-semibold tracking-tight">Detailed breakdowns</h2>
              <p className="text-xs text-muted-foreground">
                Grouped aggregates derived from the same authorized records as the summary above.
              </p>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
              <GroupedCard
                icon={<Layers className="h-4 w-4 text-blue-600" />}
                title="Pipeline by Stage"
                description="Deals created in the period, grouped by their current stage."
                canExport={canExport}
                exporting={exportingDataset === "pipeline-stage"}
                onExport={() => handleGroupedExport("pipeline-stage")}
                isPending={stage.isPending}
                isError={stage.isError}
                error={stage.error}
                isEmpty={(stage.data?.length ?? 0) === 0}
                onRetry={() => stage.refetch()}
              >
                <GroupedTable
                  caption="Pipeline by stage"
                  headers={["Stage", "Open", "Won", "Lost", "Total", "Pipeline", "Won value"]}
                  rows={(stage.data ?? []).map((r) => [
                    r.stageName,
                    r.openCount,
                    r.wonCount,
                    r.lostCount,
                    r.totalCount,
                    formatMoney(r.pipelineValue),
                    formatMoney(r.wonValue),
                  ])}
                />
              </GroupedCard>
              <GroupedCard
                icon={<UserRound className="h-4 w-4 text-violet-600" />}
                title="Pipeline by Owner"
                description="Deals created in the period, grouped by owner. Unassigned deals are shown as &quot;Unassigned&quot;."
                canExport={canExport}
                exporting={exportingDataset === "pipeline-owner"}
                onExport={() => handleGroupedExport("pipeline-owner")}
                isPending={owner.isPending}
                isError={owner.isError}
                error={owner.error}
                isEmpty={(owner.data?.length ?? 0) === 0}
                onRetry={() => owner.refetch()}
              >
                <GroupedTable
                  caption="Pipeline by owner"
                  headers={["Owner", "Open", "Won", "Lost", "Total"]}
                  rows={(owner.data ?? []).map((r) => [
                    r.ownerDisplayName ?? "Unassigned",
                    r.openCount,
                    r.wonCount,
                    r.lostCount,
                    r.totalCount,
                  ])}
                />
              </GroupedCard>
              <GroupedCard
                icon={<Building2 className="h-4 w-4 text-teal-600" />}
                title="Pipeline by Account"
                description="Deals created in the period, grouped by account. Deals with no account are excluded."
                canExport={canExport}
                exporting={exportingDataset === "pipeline-account"}
                onExport={() => handleGroupedExport("pipeline-account")}
                isPending={account.isPending}
                isError={account.isError}
                error={account.error}
                isEmpty={(account.data?.length ?? 0) === 0}
                onRetry={() => account.refetch()}
              >
                <GroupedTable
                  caption="Pipeline by account"
                  headers={["Account", "Open", "Won", "Lost", "Total"]}
                  rows={(account.data ?? []).map((r) => [
                    r.accountName ?? r.accountId,
                    r.openCount,
                    r.wonCount,
                    r.lostCount,
                    r.totalCount,
                  ])}
                />
              </GroupedCard>
              <GroupedCard
                icon={<Target className="h-4 w-4 text-blue-600" />}
                title="Conversion by Owner"
                description="Leads created in the period grouped by owner, with created-window conversion semantics."
                canExport={canExport}
                exporting={exportingDataset === "conversion-owner"}
                onExport={() => handleGroupedExport("conversion-owner")}
                isPending={conversion.isPending}
                isError={conversion.isError}
                error={conversion.error}
                isEmpty={(conversion.data?.length ?? 0) === 0}
                onRetry={() => conversion.refetch()}
              >
                <GroupedTable
                  caption="Conversion by owner"
                  headers={["Owner", "New leads", "Converted", "Rate"]}
                  rows={(conversion.data ?? []).map((r) => [
                    r.ownerDisplayName ?? "Unassigned",
                    r.newLeadCount,
                    r.convertedLeadCount,
                    formatRate(r.conversionRate),
                  ])}
                />
              </GroupedCard>
              <GroupedCard
                icon={<Hourglass className="h-4 w-4 text-amber-600" />}
                title="Deal Aging"
                description="Open deals created in the period, bucketed by their age at request time."
                canExport={canExport}
                exporting={exportingDataset === "deals-aging"}
                onExport={() => handleGroupedExport("deals-aging")}
                isPending={aging.isPending}
                isError={aging.isError}
                error={aging.error}
                isEmpty={(aging.data ?? []).every((r) => r.count === 0)}
                onRetry={() => aging.refetch()}
              >
                <GroupedTable
                  caption="Open deal aging buckets"
                  headers={["Age", "Deals", "Pipeline value"]}
                  rows={(aging.data ?? []).map((r) => [r.bucket, r.count, formatMoney(r.pipelineValue)])}
                />
              </GroupedCard>
              <GroupedCard
                icon={<PhoneCall className="h-4 w-4 text-sky-600" />}
                title="Call Status"
                description="Calls created in the period by status. Held rate excludes planned calls."
                canExport={canExport}
                exporting={exportingDataset === "calls-status"}
                onExport={() => handleGroupedExport("calls-status")}
                isPending={calls.isPending}
                isError={calls.isError}
                error={calls.error}
                isEmpty={
                  Boolean(calls.data) &&
                  (calls.data?.planned ?? 0) + (calls.data?.held ?? 0) +
                    (calls.data?.notHeld ?? 0) + (calls.data?.cancelled ?? 0) === 0
                }
                onRetry={() => calls.refetch()}
              >
                <ReportTable
                  caption="Call status summary"
                  rows={[
                    { id: "planned", label: "Planned", value: (calls.data?.planned ?? 0).toLocaleString(), hint: "Calls created in the period and still planned/scheduled." },
                    { id: "held", label: "Held", value: (calls.data?.held ?? 0).toLocaleString(), hint: "Calls created in the period that were held.", tone: "positive" },
                    { id: "not-held", label: "Not held", value: (calls.data?.notHeld ?? 0).toLocaleString(), hint: "Calls created in the period that were not held.", tone: "negative" },
                    {
                      id: "cancelled",
                      label: "Cancelled",
                      value: (calls.data?.cancelled ?? 0).toLocaleString(),
                      hint: "Calls created in the period that were cancelled.",
                      tone: (calls.data?.cancelled ?? 0) > 0 ? "negative" : "default",
                    },
                    {
                      id: "held-rate",
                      label: "Held rate",
                      value: formatRate(calls.data?.heldRate ?? 0),
                      hint: "Held / (held + not held + cancelled) - planned calls are excluded from the denominator.",
                      tone: (calls.data?.heldRate ?? 0) > 0 ? "positive" : "default",
                    },
                  ]}
                />
              </GroupedCard>
              <GroupedCard
                icon={<BarChart3 className="h-4 w-4 text-indigo-600" />}
                title="Pipeline by Forecast Category"
                description="Deals created in the period, grouped by their current, persisted forecast category. A current-data breakout - not a forecast engine."
                canExport={canExport}
                exporting={exportingDataset === "forecast-category"}
                onExport={() => handleGroupedExport("forecast-category")}
                isPending={forecastCategory.isPending}
                isError={forecastCategory.isError}
                error={forecastCategory.error}
                isEmpty={(forecastCategory.data?.length ?? 0) === 0}
                onRetry={() => forecastCategory.refetch()}
              >
                <GroupedTable
                  caption="Pipeline by forecast category"
                  headers={["Category", "Deals", "Pipeline", "Won value"]}
                  rows={(forecastCategory.data ?? []).map((r) => [
                    r.category === "UNSPECIFIED" ? "Unspecified" : r.category,
                    r.dealCount,
                    formatMoney(r.pipelineValue),
                    formatMoney(r.wonValue),
                  ])}
                />
              </GroupedCard>
              <GroupedCard
                icon={<Timer className="h-4 w-4 text-orange-600" />}
                title="Time in Current Stage"
                description="Average age and average time in the current stage for open deals, measured at request time. Current-stage snapshot, not historical stage duration."
                canExport={canExport}
                exporting={exportingDataset === "current-stage-age"}
                onExport={() => handleGroupedExport("current-stage-age")}
                isPending={currentStageAge.isPending}
                isError={currentStageAge.isError}
                error={currentStageAge.error}
                isEmpty={
                  Boolean(currentStageAge.data) &&
                  (currentStageAge.data?.openDealsWithStageEnteredAt ?? 0) +
                    (currentStageAge.data?.openDealsWithoutStageEnteredAt ?? 0) === 0
                }
                onRetry={() => currentStageAge.refetch()}
              >
                <ReportTable
                  caption="Time in current stage averages"
                  rows={[
                    {
                      id: "avg-age",
                      label: "Average deal age",
                      value: `${(currentStageAge.data?.avgDealAgeDays ?? 0).toFixed(1)} days`,
                      hint: "Average of now - created_at over open deals created in the period.",
                    },
                    {
                      id: "avg-stage",
                      label: "Average time in current stage",
                      value: `${(currentStageAge.data?.avgCurrentStageAgeDays ?? 0).toFixed(1)} days`,
                      hint: "Average of now - stage_entered_at over open deals that have a recorded stage entry time.",
                    },
                    {
                      id: "with-stage",
                      label: "Deals with stage entry time",
                      value: (currentStageAge.data?.openDealsWithStageEnteredAt ?? 0).toLocaleString(),
                      hint: "Open deals created in the period that have a trustworthy stage_entered_at.",
                    },
                    {
                      id: "without-stage",
                      label: "Deals without stage entry time",
                      value: (currentStageAge.data?.openDealsWithoutStageEnteredAt ?? 0).toLocaleString(),
                      hint: "Open deals created in the period with stage_entered_at missing; excluded from the current-stage average.",
                    },
                  ]}
                />
              </GroupedCard>
              <GroupedCard
                icon={<ListChecks className="h-4 w-4 text-green-700" />}
                title="Activity Rates"
                description="Task completion and overdue rates, plus meeting status distribution and held rate, for the selected period."
                canExport={canExport}
                exporting={exportingDataset === "activity-rates"}
                onExport={() => handleGroupedExport("activity-rates")}
                isPending={activityRates.isPending}
                isError={activityRates.isError}
                error={activityRates.error}
                isEmpty={Boolean(activityRates.data) && !activityRates.data?.meetingStatus}
                onRetry={() => activityRates.refetch()}
              >
                <ReportTable
                  caption="Activity rates"
                  rows={[
                    {
                      id: "task-completion",
                      label: "Task completion rate",
                      value: formatRate(activityRates.data?.taskCompletionRate ?? 0),
                      hint: "Tasks created and completed in the period / all tasks created in the period.",
                      tone: (activityRates.data?.taskCompletionRate ?? 0) > 0 ? "positive" : "default",
                    },
                    {
                      id: "task-overdue",
                      label: "Task overdue rate",
                      value: formatRate(activityRates.data?.taskOverdueRate ?? 0),
                      hint: "Overdue open tasks / open tasks created in the period.",
                      tone: (activityRates.data?.taskOverdueRate ?? 0) > 0 ? "negative" : "default",
                    },
                    {
                      id: "meeting-held",
                      label: "Meetings held",
                      value: (activityRates.data?.meetingStatus.held ?? 0).toLocaleString(),
                      hint: "Meetings created in the period that were held.",
                      tone: "positive",
                    },
                    {
                      id: "meeting-not-held",
                      label: "Meetings not held",
                      value: (activityRates.data?.meetingStatus.notHeld ?? 0).toLocaleString(),
                      hint: "Meetings created in the period not held.",
                      tone: "negative",
                    },
                    {
                      id: "meeting-cancelled",
                      label: "Meetings cancelled",
                      value: (activityRates.data?.meetingStatus.cancelled ?? 0).toLocaleString(),
                      hint: "Meetings created in the period cancelled.",
                      tone: (activityRates.data?.meetingStatus.cancelled ?? 0) > 0 ? "negative" : "default",
                    },
                    {
                      id: "meeting-held-rate",
                      label: "Meeting held rate",
                      value: formatRate(activityRates.data?.meetingStatus.heldRate ?? 0),
                      hint: "Held / (held + not held + cancelled) - planned meetings excluded from the denominator.",
                      tone: (activityRates.data?.meetingStatus.heldRate ?? 0) > 0 ? "positive" : "default",
                    },
                  ]}
                />
              </GroupedCard>
              <GroupedCard
                icon={<PhoneCall className="h-4 w-4 text-sky-600" />}
                title="Call Duration"
                description="Call duration metrics using only calls with a captured duration. Calls without captured duration are reported separately, never treated as zero."
                canExport={canExport}
                exporting={exportingDataset === "calls-duration"}
                onExport={() => handleGroupedExport("calls-duration")}
                isPending={callDuration.isPending}
                isError={callDuration.isError}
                error={callDuration.error}
                isEmpty={Boolean(callDuration.data) && (callDuration.data?.callsTotal ?? 0) === 0}
                onRetry={() => callDuration.refetch()}
              >
                <ReportTable
                  caption="Call duration metrics"
                  rows={[
                    {
                      id: "with-duration",
                      label: "Calls with captured duration",
                      value: (callDuration.data?.callsWithDuration ?? 0).toLocaleString(),
                      hint: "Calls created in the period with a non-null duration.",
                      tone: "positive",
                    },
                    {
                      id: "total",
                      label: "All calls",
                      value: (callDuration.data?.callsTotal ?? 0).toLocaleString(),
                      hint: "Total calls created in the period.",
                    },
                    {
                      id: "without-duration",
                      label: "Calls without captured duration",
                      value: (callDuration.data?.callsWithoutDuration ?? 0).toLocaleString(),
                      hint: "Calls created in the period with no captured duration - reported separately, not counted as zero.",
                    },
                    {
                      id: "coverage",
                      label: "Duration coverage",
                      value: callDuration.data && callDuration.data.callsTotal > 0
                        ? formatRate((callDuration.data.callsWithDuration / callDuration.data.callsTotal) * 100)
                        : "\u2014",
                      hint: "Calls with captured duration / all calls. Null durations are never treated as zero.",
                    },
                    {
                      id: "total-minutes",
                      label: "Total call minutes",
                      value: (callDuration.data?.totalCallMinutes ?? 0).toLocaleString(),
                      hint: "Sum of captured duration.minutes over calls with a duration.",
                    },
                    {
                      id: "avg-duration",
                      label: "Average call duration",
                      value: `${(callDuration.data?.averageCallDurationMinutes ?? 0).toFixed(1)} min`,
                      hint: "Total captured minutes / calls with captured duration.",
                    },
                  ]}
                />
              </GroupedCard>
              <GroupedCard
                icon={<Tag className="h-4 w-4 text-pink-600" />}
                title="Lead Source"
                description="Leads created in the period grouped by their captured source. Unspecified-source leads are reported, not hidden."
                canExport={canExport}
                exporting={exportingDataset === "lead-source"}
                onExport={() => handleGroupedExport("lead-source")}
                isPending={leadSource.isPending}
                isError={leadSource.isError}
                error={leadSource.error}
                isEmpty={(leadSource.data?.length ?? 0) === 0}
                onRetry={() => leadSource.refetch()}
              >
                <GroupedTable
                  caption="Lead source performance"
                  headers={["Source", "Leads", "Converted", "Rate"]}
                  rows={(leadSource.data ?? []).map((r) => [
                    r.source === "UNSPECIFIED" ? "Unspecified" : r.source,
                    r.leadCount,
                    r.convertedCount,
                    formatRate(r.conversionRate),
                  ])}
                />
              </GroupedCard>
              <GroupedCard
                icon={<Contact className="h-4 w-4 text-cyan-600" />}
                title="Contacts per Account"
                description="Contacts created in the period grouped by their account. Accounts with no matching account are reported, not hidden."
                canExport={canExport}
                exporting={exportingDataset === "contacts-account"}
                onExport={() => handleGroupedExport("contacts-account")}
                isPending={contactsAccount.isPending}
                isError={contactsAccount.isError}
                error={contactsAccount.error}
                isEmpty={(contactsAccount.data?.length ?? 0) === 0}
                onRetry={() => contactsAccount.refetch()}
              >
                <GroupedTable
                  caption="Contacts per account"
                  headers={["Account", "Contacts"]}
                  rows={(contactsAccount.data ?? []).map((r) => [
                    r.accountName === "NO ACCOUNT" ? "No account" : r.accountName,
                    r.contactCount,
                  ])}
                />
              </GroupedCard>
              <GroupedCard
                icon={<UserRound className="h-4 w-4 text-violet-600" />}
                title="Accounts by Owner"
                description="Accounts created in the period grouped by their owner. Unassigned accounts are reported, not hidden."
                canExport={canExport}
                exporting={exportingDataset === "accounts-owner"}
                onExport={() => handleGroupedExport("accounts-owner")}
                isPending={accountsOwner.isPending}
                isError={accountsOwner.isError}
                error={accountsOwner.error}
                isEmpty={(accountsOwner.data?.length ?? 0) === 0}
                onRetry={() => accountsOwner.refetch()}
              >
                <GroupedTable
                  caption="Accounts by owner"
                  headers={["Owner", "Accounts", "Active"]}
                  rows={(accountsOwner.data ?? []).map((r) => [
                    r.ownerDisplayName ?? "Unassigned",
                    r.accountCount,
                    r.activeCount,
                  ])}
                />
              </GroupedCard>
            </div>
          </section>
          <p className="text-xs text-muted-foreground text-center">
            Showing data from {new Date(data.from).toLocaleDateString()} to {new Date(data.to).toLocaleDateString()}
          </p>
        </>
      ) : null}
    </div>
  );
}