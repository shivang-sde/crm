"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { entitlementApi } from "@/lib/api/entitlements";
import {
  CustomerEntitlementListParams,
  CustomerEntitlementUpdateRequest,
} from "@/types/entitlements";

const entitlementKeys = {
  all: ["entitlements"] as const,
  lists: () => [...entitlementKeys.all, "list"] as const,
  list: (params: CustomerEntitlementListParams) => [...entitlementKeys.lists(), params] as const,
  detail: (id: string) => [...entitlementKeys.all, "detail", id] as const,
};

export function useEntitlements(params: CustomerEntitlementListParams = {}) {
  return useQuery({
    queryKey: entitlementKeys.list(params),
    queryFn: () => entitlementApi.listEntitlements(params),
  });
}

export function useEntitlement(id?: string) {
  return useQuery({
    queryKey: entitlementKeys.detail(id ?? ""),
    queryFn: () => entitlementApi.getEntitlement(id ?? ""),
    enabled: Boolean(id),
  });
}

export function useUpdateEntitlement() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: CustomerEntitlementUpdateRequest }) =>
      entitlementApi.updateEntitlement(id, data),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: entitlementKeys.lists() });
      queryClient.invalidateQueries({ queryKey: entitlementKeys.detail(id) });
      toast.success("Entitlement updated");
    },
    onError: () => toast.error("Failed to update entitlement"),
  });
}

export function useActivateEntitlement() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => entitlementApi.activateEntitlement(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: entitlementKeys.lists() });
      queryClient.invalidateQueries({ queryKey: entitlementKeys.detail(id) });
      toast.success("Entitlement activated");
    },
    onError: () => toast.error("Failed to activate entitlement"),
  });
}

export function useSuspendEntitlement() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => entitlementApi.suspendEntitlement(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: entitlementKeys.lists() });
      queryClient.invalidateQueries({ queryKey: entitlementKeys.detail(id) });
      toast.success("Entitlement suspended");
    },
    onError: () => toast.error("Failed to suspend entitlement"),
  });
}

export function useTerminateEntitlement() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => entitlementApi.terminateEntitlement(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: entitlementKeys.lists() });
      queryClient.invalidateQueries({ queryKey: entitlementKeys.detail(id) });
      toast.success("Entitlement terminated");
    },
    onError: () => toast.error("Failed to terminate entitlement"),
  });
}
