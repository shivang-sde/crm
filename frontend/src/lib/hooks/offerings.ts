import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { offeringApi } from "@/lib/api/offerings";
import { OfferingCreateRequest, OfferingListParams, OfferingUpdateRequest } from "@/types/offerings";

const offeringKeys = {
  all: ["offerings"] as const,
  lists: () => [...offeringKeys.all, "list"] as const,
  list: (params: OfferingListParams) => [...offeringKeys.lists(), params] as const,
  detail: (id: string) => [...offeringKeys.all, "detail", id] as const,
};

export function useOfferings(params: OfferingListParams = {}) {
  return useQuery({
    queryKey: offeringKeys.list(params),
    queryFn: () => offeringApi.listOfferings(params),
  });
}

export function useOffering(id?: string) {
  return useQuery({
    queryKey: offeringKeys.detail(id ?? ""),
    queryFn: () => offeringApi.getOffering(id ?? ""),
    enabled: Boolean(id),
  });
}

export function useCreateOffering() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (data: OfferingCreateRequest) => offeringApi.createOffering(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: offeringKeys.lists() });
    },
  });
}

export function useUpdateOffering() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: OfferingUpdateRequest }) => offeringApi.updateOffering(id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: offeringKeys.lists() });
    },
  });
}

export function useToggleOfferingStatus() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      active ? offeringApi.activateOffering(id) : offeringApi.deactivateOffering(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: offeringKeys.lists() });
    },
  });
}

export function useDeleteOffering() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => offeringApi.deleteOffering(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: offeringKeys.lists() });
    },
  });
}
