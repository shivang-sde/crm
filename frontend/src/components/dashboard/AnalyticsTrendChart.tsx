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
        <ResponsiveContainer width="100%" height={320}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
            <XAxis
              dataKey="label"
              tick={{ fontSize: 12 }}
              className="text-muted-foreground"
            />
            <YAxis
              allowDecimals={false}
              tick={{ fontSize: 12 }}
              className="text-muted-foreground"
            />
            <Tooltip />
            <Legend />
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
      </CardContent>
    </Card>
  );
}
