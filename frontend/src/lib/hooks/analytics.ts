"use client";

import { useQuery, type UseQueryOptions } from "@tanstack/react-query";
import { analyticsApi } from "@/lib/api/analytics";
import {
  AnalyticsDateRange,
  AnalyticsSummaryResponse,
  AnalyticsTrendPoint,
  PipelineStageRow,
  PipelineOwnerRow,
  PipelineAccountRow,
  ConversionOwnerRow,
  DealAgingRow,
  CallStatusSummary,
  ContactsPerAccountRow,
  ConversionPeriodSummary,
  ForecastCategoryRow,
  CurrentStageAgeSummary,
  ActivityRatesSummary,
  CallDurationSummary,
  LeadSourcePerformanceRow,
  AccountsByOwnerRow,
} from "@/types/analytics";

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

function groupedQueryKey(prefix: string, range?: AnalyticsDateRange, tenantId?: string): unknown[] {
  return [prefix, range?.from ?? null, range?.to ?? null, tenantId ?? null];
}

export function usePipelineByStage(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<PipelineStageRow[]>) {
  return useQuery({
    queryKey: groupedQueryKey("analytics-pipeline-stage", range, tenantId),
    queryFn: () => analyticsApi.getPipelineByStage(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}

export function usePipelineByOwner(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<PipelineOwnerRow[]>) {
  return useQuery({
    queryKey: groupedQueryKey("analytics-pipeline-owner", range, tenantId),
    queryFn: () => analyticsApi.getPipelineByOwner(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}

export function usePipelineByAccount(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<PipelineAccountRow[]>) {
  return useQuery({
    queryKey: groupedQueryKey("analytics-pipeline-account", range, tenantId),
    queryFn: () => analyticsApi.getPipelineByAccount(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}

export function useConversionByOwner(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<ConversionOwnerRow[]>) {
  return useQuery({
    queryKey: groupedQueryKey("analytics-conversion-owner", range, tenantId),
    queryFn: () => analyticsApi.getConversionByOwner(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}

export function useDealAging(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<DealAgingRow[]>) {
  return useQuery({
    queryKey: groupedQueryKey("analytics-deal-aging", range, tenantId),
    queryFn: () => analyticsApi.getDealAging(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}

export function useCallStatus(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<CallStatusSummary>) {
  return useQuery({
    queryKey: groupedQueryKey("analytics-call-status", range, tenantId),
    queryFn: () => analyticsApi.getCallStatus(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}

export function useConversionDuringPeriod(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<ConversionPeriodSummary>) {
  return useQuery({
    queryKey: groupedQueryKey("analytics-conversion-period", range, tenantId),
    queryFn: () => analyticsApi.getConversionDuringPeriod(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}

export function usePipelineByForecastCategory(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<ForecastCategoryRow[]>) {
  return useQuery({
    queryKey: groupedQueryKey("analytics-forecast-category", range, tenantId),
    queryFn: () => analyticsApi.getPipelineByForecastCategory(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}

export function useCurrentStageAge(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<CurrentStageAgeSummary>) {
  return useQuery({
    queryKey: groupedQueryKey("analytics-current-stage-age", range, tenantId),
    queryFn: () => analyticsApi.getCurrentStageAge(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}

export function useActivityRates(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<ActivityRatesSummary>) {
  return useQuery({
    queryKey: groupedQueryKey("analytics-activity-rates", range, tenantId),
    queryFn: () => analyticsApi.getActivityRates(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}

export function useCallDuration(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<CallDurationSummary>) {
  return useQuery({
    queryKey: groupedQueryKey("analytics-call-duration", range, tenantId),
    queryFn: () => analyticsApi.getCallDuration(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}

export function useLeadSourcePerformance(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<LeadSourcePerformanceRow[]>) {
  return useQuery({
    queryKey: groupedQueryKey("analytics-lead-source", range, tenantId),
    queryFn: () => analyticsApi.getLeadSourcePerformance(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}

export function useContactsPerAccount(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<ContactsPerAccountRow[]>) {
  return useQuery({
    queryKey: groupedQueryKey("analytics-contacts-account", range, tenantId),
    queryFn: () => analyticsApi.getContactsPerAccount(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}

export function useAccountsByOwner(range?: AnalyticsDateRange, tenantId?: string, options?: QueryOptions<AccountsByOwnerRow[]>) {
  return useQuery({
    queryKey: groupedQueryKey("analytics-accounts-owner", range, tenantId),
    queryFn: () => analyticsApi.getAccountsByOwner(range, tenantId),
    staleTime: 30_000,
    ...options,
  });
}
