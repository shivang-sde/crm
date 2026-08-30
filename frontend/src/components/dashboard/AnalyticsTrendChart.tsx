"use client";

import { useMemo } from "react";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";
import type { TooltipContentProps } from "recharts";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { BarChart3 } from "lucide-react";
import type { AnalyticsTrendPoint } from "@/types/analytics";

const SERIES = [
  { key: "leads" as const, label: "Leads", color: "#3b82f6" },
  { key: "contacts" as const, label: "Contacts", color: "#8b5cf6" },
  { key: "deals" as const, label: "Deals", color: "#10b981" },
  { key: "tasks" as const, label: "Tasks", color: "#f59e0b" },
];

function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function TrendTooltip({ active, payload, label }: TooltipContentProps) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-lg border bg-background px-3 py-2 text-xs shadow-sm">
      <p className="mb-1.5 font-medium text-muted-foreground">{label}</p>
      {payload.map((entry) => (
        <div key={String(entry.dataKey)} className="flex items-center gap-2 py-0.5 leading-none">
          <span
            className="h-2 w-2 shrink-0 rounded-full"
            style={{ backgroundColor: entry.color ?? "#8884d8" }}
          />
          <span className="text-muted-foreground">{entry.name}</span>
          <span className="ml-auto pl-4 font-medium tabular-nums">
            {typeof entry.value === "number" ? entry.value.toLocaleString() : String(entry.value ?? "")}
          </span>
        </div>
      ))}
    </div>
  );
}

interface AnalyticsTrendChartProps {
  data: AnalyticsTrendPoint[];
}

export function AnalyticsTrendChart({ data }: AnalyticsTrendChartProps) {
  const chartData = useMemo(
    () =>
      data.map((point) => ({
        ...point,
        label: formatDate(point.bucket),
      })),
    [data]
  );

  return (
    <Card className="shadow-sm border border-muted">
      <CardHeader className="pb-2">
        <CardTitle className="text-base font-semibold flex items-center gap-2">
          <BarChart3 className="h-4 w-4 text-blue-600" />
          CRM Activity Trends
        </CardTitle>
      </CardHeader>
      <CardContent>
        {chartData.length === 0 ? (
          <p className="py-10 text-center text-sm text-muted-foreground">
            No trend data in this period.
          </p>
        ) : (
          <ResponsiveContainer width="100%" height={320}>
            <LineChart data={chartData} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
              <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
              <XAxis
                dataKey="label"
                tick={{ fontSize: 12 }}
                tickMargin={8}
                interval="preserveStartEnd"
                className="text-muted-foreground"
              />
              <YAxis
                allowDecimals={false}
                tick={{ fontSize: 12 }}
                className="text-muted-foreground"
              />
              <Tooltip content={(props) => <TrendTooltip {...props} />} />
              <Legend iconSize={10} />
              {SERIES.map((s) => (
                <Line
                  key={s.key}
                  type="monotone"
                  dataKey={s.key}
                  name={s.label}
                  stroke={s.color}
                  strokeWidth={2}
                  dot={false}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
        )}
      </CardContent>
    </Card>
  );
}
