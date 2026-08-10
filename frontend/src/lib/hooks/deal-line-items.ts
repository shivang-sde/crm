import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { dealLineItemApi } from "@/lib/api/deal-line-items";
import { DealLineItemCreateRequest, DealLineItemUpdateRequest } from "@/types/deal-line-items";

const dealLineItemKeys = {
  all: ["deal-line-items"] as const,
  byDeal: (dealId: string) => [...dealLineItemKeys.all, dealId] as const,
};

export function useDealLineItems(dealId?: string) {
  return useQuery({
    queryKey: dealLineItemKeys.byDeal(dealId ?? ""),
    queryFn: () => dealLineItemApi.listDealLineItems(dealId ?? ""),
    enabled: Boolean(dealId),
  });
}

export function useCreateDealLineItem(dealId?: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (data: DealLineItemCreateRequest) => dealLineItemApi.createDealLineItem(dealId ?? "", data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: dealLineItemKeys.byDeal(dealId ?? "") });
    },
  });
}

export function useUpdateDealLineItem(dealId?: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: DealLineItemUpdateRequest }) =>
      dealLineItemApi.updateDealLineItem(dealId ?? "", id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: dealLineItemKeys.byDeal(dealId ?? "") });
    },
  });
}

export function useDeleteDealLineItem(dealId?: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => dealLineItemApi.deleteDealLineItem(dealId ?? "", id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: dealLineItemKeys.byDeal(dealId ?? "") });
    },
  });
}
