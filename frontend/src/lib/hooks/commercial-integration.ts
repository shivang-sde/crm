"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { commercialIntegrationApi, CommercialDocumentListParams } from "@/lib/api/commercial-integration";

export function useCommercialKeyStatus() {
  return useQuery({
    queryKey: ["commercial-key-status"],
    queryFn: () => commercialIntegrationApi.getStatus(),
    staleTime: 30_000,
  });
}

export function useCommercialRotate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => commercialIntegrationApi.rotate(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["commercial-key-status"] });
    },
  });
}

export function useCommercialDocuments(params: CommercialDocumentListParams) {
  const enabled = !!(params.accountId || params.contactId);
  return useQuery({
    queryKey: ["commercial-documents", params],
    queryFn: () => commercialIntegrationApi.listDocuments(params),
    enabled,
    staleTime: 30_000,
  });
}
