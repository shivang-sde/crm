"use client";

import { Loader2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { useSalesDashboard } from "@/lib/hooks/deals";
import { SalesDashboardResponse } from "@/types/sales-dashboard";

function formatMoney(value: number | null | undefined): string {
  if (value === null || value === undefined) return "—";
  return value.toLocaleString(undefined, { maximumFractionDigits: 0 });
}

function KpiCard({
  label,
  value,
  sub,
  tone = "default",
  sub2,
}: {
  label: string;
  value: string;
  sub?: string;
  sub2?: string;
  tone?: "default" | "positive" | "negative" | "warning";
}) {
  const tones: Record<string, string> = {
    default: "bg-blue-50 text-blue-600",
    positive: "bg-emerald-50 text-emerald-600",
    negative: "bg-rose-50 text-rose-600",
    warning: "bg-amber-50 text-amber-600",
  };
  return (
    <Card className="shadow-sm border border-muted">
      <CardContent className="pt-4">
        <div className="flex items-center gap-3">
          <span className={`inline-block h-2 w-2 rounded-full ${tones[tone].split(" ")[1]}`} />
          <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">{label}</p>
        </div>
        <p className="mt-2 text-2xl font-bold text-foreground">{value}</p>
        {sub && <p className="mt-1 text-xs text-muted-foreground">{sub}</p>}
        {sub2 && <p className="text-[11px] text-muted-foreground/80">{sub2}</p>}
      </CardContent>
    </Card>
  );
}

export function SalesDashboard() {
  const { data, isLoading, isError } = useSalesDashboard();

  if (isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="py-16 text-center text-sm text-destructive">
        Unable to load sales dashboard data.
      </div>
    );
  }

  const d = data.deals;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <KpiCard
          label="Open pipeline"
          value={formatMoney(d.open_pipeline_value)}
          sub={`${d.openCount} open deal${d.openCount === 1 ? "" : "s"}`}
        />
        <KpiCard
          label="Weighted pipeline"
          value={formatMoney(d.weighted_pipeline_value)}
          sub="Sum of expected revenue on open deals"
        />
        <KpiCard
          label="Won / Lost"
          value={`${d.wonCount} / ${d.lostCount}`}
          sub={`Won value ${formatMoney(d.won_value)}`}
          tone="positive"
        />
        <KpiCard
          label="Conversion rate"
          value={`${data.lead_funnel.conversion_rate_percent}%`}
          sub={`${data.lead_funnel.converted_leads} of ${data.lead_funnel.total_leads} leads converted`}
          tone="positive"
        />
        <KpiCard
          label="Average open deal size"
          value={formatMoney(d.average_open_deal_size)}
          sub={`${d.lostCount} lost (${formatMoney(d.lost_value)})`}
          tone="warning"
        />
        <KpiCard
          label="Avg days in pipeline"
          value={
            d.average_days_in_pipeline != null
              ? `${d.average_days_in_pipeline} days`
              : "—"
          }
          sub={
            d.max_open_deal_age_days != null
              ? `Oldest open deal: ${d.max_open_deal_age_days} days · measured from deal creation to today`
              : "Average age of currently open deals, from deal creation to today"
          }
        />
        <KpiCard
          label="Avg days in current stage"
          value={
            d.average_days_in_current_stage != null
              ? `${d.average_days_in_current_stage} days`
              : "—"
          }
          sub="Average age of currently open deals in their current stage"
        />
        <KpiCard
          label="Stale deals"
          value={String(d.stale_deal_count)}
          sub={`${formatMoney(d.stale_deal_value)} pipeline · ${formatMoney(d.stale_deal_weighted_value)} weighted${
            d.stale_deal_percentage != null ? ` · ${d.stale_deal_percentage}% of open deals` : ""
          }`}
          sub2="Open deals in their current stage for 30+ days"
          tone={d.stale_deal_count > 0 ? "negative" : "default"}
        />
        <KpiCard
          label="Closing next 30 days"
          value={String(data.closing.expected_close_next_30_days_count)}
          sub={`Value ${formatMoney(data.closing.expected_close_next_30_days_value)}`}
        />
        <KpiCard
          label="Overdue expected close"
          value={String(data.closing.overdue_expected_close_count)}
          sub={`Value ${formatMoney(data.closing.overdue_expected_close_value)}`}
          tone={data.closing.overdue_expected_close_count > 0 ? "negative" : "default"}
        />
        <KpiCard
          label="Avg sales cycle"
          value={
            data.closing.average_sales_cycle_days != null
              ? `${data.closing.average_sales_cycle_days} days`
              : "—"
          }
          sub={`Won last 30 days: ${formatMoney(data.closing.won_value_last_30_days)}`}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card className="shadow-sm border border-muted">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-semibold">Pipeline by stage</CardTitle>
          </CardHeader>
          <CardContent>
            {data.stage_breakdown.length === 0 ? (
              <p className="text-sm text-muted-foreground">No stages configured.</p>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs uppercase tracking-wider text-muted-foreground">
                    <th className="pb-2 font-medium">Stage</th>
                    <th className="pb-2 font-medium text-right">Deals</th>
                    <th className="pb-2 font-medium text-right">Amount</th>
                    <th className="pb-2 font-medium text-right">Avg days in stage</th>
                    <th className="pb-2 font-medium text-right">Stale</th>
                  </tr>
                </thead>
                <tbody>
                  {data.stage_breakdown.map((stage) => (
                    <tr key={stage.stageId} className="border-t">
                      <td className="py-2">
                        <span className="inline-flex items-center gap-2">
                          <span
                            className="inline-block h-2.5 w-2.5 rounded-full"
                            style={{ backgroundColor: stage.color || "#6366f1" }}
                          />
                          {stage.stageName}
                          {stage.recordCategory && stage.recordCategory !== "OPEN" && (
                            <Badge variant="outline" className="text-[10px]">
                              {stage.recordCategory.replace("CLOSED_", "")}
                            </Badge>
                          )}
                        </span>
                      </td>
                      <td className="py-2 text-right">{stage.count}</td>
                      <td className="py-2 text-right">{formatMoney(stage.total_amount)}</td>
                      <td className="py-2 text-right">
                        {stage.average_days_in_stage != null
                          ? `${stage.average_days_in_stage} days`
                          : "—"}
                      </td>
                      <td className={`py-2 text-right ${stage.stale_count > 0 ? "font-medium text-destructive" : ""}`}>
                        {stage.stale_count}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </CardContent>
        </Card>

        <Card className="shadow-sm border border-muted">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-semibold">Pipeline by owner</CardTitle>
          </CardHeader>
          <CardContent>
            {data.owner_breakdown.length === 0 ? (
              <p className="text-sm text-muted-foreground">No deals in your visibility scope.</p>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs uppercase tracking-wider text-muted-foreground">
                    <th className="pb-2 font-medium">Owner</th>
                    <th className="pb-2 font-medium text-right">Open</th>
                    <th className="pb-2 font-medium text-right">Open value</th>
                    <th className="pb-2 font-medium text-right">Won</th>
                    <th className="pb-2 font-medium text-right">Won value</th>
                  </tr>
                </thead>
                <tbody>
                  {data.owner_breakdown.map((owner) => (
                    <tr key={owner.owner_user_id ?? "unassigned"} className="border-t">
                      <td className="py-2">{owner.owner_name}</td>
                      <td className="py-2 text-right">{owner.open_count}</td>
                      <td className="py-2 text-right">{formatMoney(owner.open_value)}</td>
                      <td className="py-2 text-right">{owner.won_count}</td>
                      <td className="py-2 text-right">{formatMoney(owner.won_value)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <KpiCard label="Total leads" value={String(data.lead_funnel.total_leads)} />
        <KpiCard label="Open leads" value={String(data.lead_funnel.open_leads)} tone="warning" />
        <KpiCard label="Converted leads" value={String(data.lead_funnel.converted_leads)} tone="positive" />
      </div>
    </div>
  );
}
