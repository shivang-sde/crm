"use client";

import { useQuery, type UseQueryOptions } from "@tanstack/react-query";
import { analyticsApi } from "@/lib/api/analytics";
import { AnalyticsDateRange, AnalyticsSummaryResponse, AnalyticsTrendPoint } from "@/types/analytics";

type QueryOptions<T> = Omit<UseQueryOptions<T>, "queryKey" | "queryFn">;

export function useAnalyticsSummary(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<AnalyticsSummaryResponse>) {
  return useQuery({
    queryKey: ["analytics-summary", range?.from ?? null, range?.to ?? null, tenantId ?? null],
    queryFn: () => analyticsApi.getSummary(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}

export function useAnalyticsTrends(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<AnalyticsTrendPoint[]>) {
  return useQuery({
    queryKey: ["analytics-trends", range?.from ?? null, range?.to ?? null, tenantId ?? null],
    queryFn: () => analyticsApi.getTrends(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}
