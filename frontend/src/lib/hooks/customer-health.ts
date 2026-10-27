"use client";

import { useQuery } from "@tanstack/react-query";

import { customerHealthApi } from "@/lib/api/customer-health";
import type {
  RiskSignalAccountParams,
  RiskSignalListParams,
} from "@/types/customer-health";

export function useAccountRiskSignals(
  accountId: string | undefined,
  params: RiskSignalAccountParams = {},
  enabled = true,
) {
  return useQuery({
    queryKey: ["customer-health", "account", accountId, params],
    queryFn: () => customerHealthApi.listAccountRiskSignals(accountId!, params),
    enabled: !!accountId && enabled,
  });
}

export function useRiskSignals(
  params: RiskSignalListParams = {},
  enabled = true,
) {
  return useQuery({
    queryKey: ["customer-health", "risk-signals", params],
    queryFn: () => customerHealthApi.listRiskSignals(params),
    enabled,
  });
}
