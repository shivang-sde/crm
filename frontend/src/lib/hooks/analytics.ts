"use client";

import { useQuery } from "@tanstack/react-query";
import { analyticsApi } from "@/lib/api/analytics";
import { AnalyticsDateRange } from "@/types/analytics";

export function useAnalyticsSummary(range?: AnalyticsDateRange, tenantId?: string) {
  return useQuery({
    queryKey: ["analytics-summary", range?.from ?? null, range?.to ?? null, tenantId ?? null],
    queryFn: () => analyticsApi.getSummary(range, tenantId),
    staleTime: 30_000,
  });
}

export function useAnalyticsTrends(range?: AnalyticsDateRange, tenantId?: string) {
  return useQuery({
    queryKey: ["analytics-trends", range?.from ?? null, range?.to ?? null, tenantId ?? null],
    queryFn: () => analyticsApi.getTrends(range, tenantId),
    staleTime: 30_000,
  });
}
