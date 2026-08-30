"use client";

import { useQuery } from "@tanstack/react-query";
import { tenantApi } from "@/lib/api/tenants";

export function useTenants() {
  return useQuery({
    queryKey: ["tenants"],
    queryFn: () => tenantApi.getAllTenants(),
    staleTime: 30_000,
  });
}