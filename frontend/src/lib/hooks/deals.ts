"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { dealApi } from "@/lib/api/deals";
import { dealStageApi } from "@/lib/api/deal-stages";
import { dealCustomFieldApi } from "@/lib/api/deal-custom-fields";
import { DealCreateRequest, DealUpdateRequest, DealListParams } from "@/types/deals";
import { DealStageCreateRequest } from "@/types/deal-stages";
import { DealCustomFieldCreateRequest } from "@/types/deal-custom-fields";

export function useDeals(params: DealListParams = {}) {
  return useQuery({ queryKey: ["deals", params], queryFn: () => dealApi.listDeals(params) });
}

export function useSalesDashboard() {
  return useQuery({ queryKey: ["sales-dashboard"], queryFn: () => dealApi.getDashboard() });
}

export function useDeal(id: string | undefined) {
  return useQuery({ queryKey: ["deals", id], queryFn: () => dealApi.getDeal(id!), enabled: !!id });
}

export function useDealStages() {
  return useQuery({ queryKey: ["deal-stages"], queryFn: () => dealStageApi.listStages() });
}

export function useDealCustomFields() {
  return useQuery({ queryKey: ["deal-custom-fields"], queryFn: () => dealCustomFieldApi.listCustomFields() });
}

export function useDealActivities(dealId: string | undefined) {
  return useQuery({ queryKey: ["deal-activities", dealId], queryFn: () => dealApi.getActivities(dealId!), enabled: !!dealId });
}

export function useDealNotes(dealId: string | undefined) {
  return useQuery({ queryKey: ["deal-notes", dealId], queryFn: () => dealApi.getNotes(dealId!), enabled: !!dealId });
}

export function useAddDealNote() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ dealId, note }: { dealId: string; note: string }) => dealApi.addNote(dealId, note),
    onSuccess: (_, { dealId }) => {
      queryClient.invalidateQueries({ queryKey: ["deal-notes", dealId] });
      queryClient.invalidateQueries({ queryKey: ["deals", dealId] });
      toast.success("Note added");
    },
    onError: () => toast.error("Failed to add note"),
  });
}

export function useDeleteDealNote() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ dealId, noteId }: { dealId: string; noteId: string }) => dealApi.deleteNote(dealId, noteId),
    onSuccess: (_, { dealId }) => {
      queryClient.invalidateQueries({ queryKey: ["deal-notes", dealId] });
      queryClient.invalidateQueries({ queryKey: ["deals", dealId] });
      toast.success("Note deleted");
    },
    onError: () => toast.error("Failed to delete note"),
  });
}

export function useCreateDeal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: DealCreateRequest) => dealApi.createDeal(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deals"] });
      toast.success("Deal created successfully");
    },
    onError: (error: any) => toast.error(error?.response?.data?.error?.message || "Failed to create deal"),
  });
}

export function useUpdateDeal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: DealUpdateRequest }) => dealApi.updateDeal(id, data),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["deals"] });
      queryClient.invalidateQueries({ queryKey: ["deals", id] });
      toast.success("Deal updated successfully");
    },
    onError: () => toast.error("Failed to update deal"),
  });
}

export function useDeleteDeal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => dealApi.deleteDeal(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deals"] });
      toast.success("Deal deleted successfully");
    },
    onError: () => toast.error("Failed to delete deal"),
  });
}

export function useChangeDealStage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, stageId, wonReason, lostReason }: { id: string; stageId: string; wonReason?: string; lostReason?: string }) =>
      dealApi.changeStage(id, stageId, { wonReason, lostReason }),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["deals"] });
      queryClient.invalidateQueries({ queryKey: ["deals", id] });
      queryClient.invalidateQueries({ queryKey: ["deal-activities", id] });
      toast.success("Deal stage updated");
    },
    onError: () => toast.error("Failed to change deal stage"),
  });
}

export function useAssignDeal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ownerUserId }: { id: string; ownerUserId: string }) => dealApi.assignDeal(id, ownerUserId),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["deals"] });
      queryClient.invalidateQueries({ queryKey: ["deals", id] });
      queryClient.invalidateQueries({ queryKey: ["deal-activities", id] });
      toast.success("Deal assigned successfully");
    },
    onError: () => toast.error("Failed to assign deal"),
  });
}

export function useMarkDealWon() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, wonReason, stageId }: { id: string; wonReason?: string; stageId?: string }) => dealApi.markWon(id, wonReason, stageId),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["deals"] });
      queryClient.invalidateQueries({ queryKey: ["deals", id] });
      toast.success("Deal marked as won");
    },
    onError: () => toast.error("Failed to mark deal won"),
  });
}

export function useMarkDealLost() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, lostReason, stageId }: { id: string; lostReason?: string; stageId?: string }) => dealApi.markLost(id, lostReason, stageId),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["deals"] });
      queryClient.invalidateQueries({ queryKey: ["deals", id] });
      toast.success("Deal marked as lost");
    },
    onError: () => toast.error("Failed to mark deal lost"),
  });
}

// Stages and custom field admin hooks
export function useCreateDealStage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: DealStageCreateRequest) => dealStageApi.createStage(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deal-stages"] });
      toast.success("Stage created");
    },
    onError: () => toast.error("Failed to create stage"),
  });
}

export function useUpdateDealStage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: DealStageCreateRequest }) => dealStageApi.updateStage(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deal-stages"] });
      toast.success("Stage updated");
    },
    onError: () => toast.error("Failed to update stage"),
  });
}

export function useDeleteDealStage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => dealStageApi.deleteStage(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deal-stages"] });
      toast.success("Stage deleted");
    },
    onError: () => toast.error("Failed to delete stage"),
  });
}

export function useCreateDealCustomField() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: DealCustomFieldCreateRequest) => dealCustomFieldApi.createCustomField(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deal-custom-fields"] });
      toast.success("Custom field created");
    },
    onError: () => toast.error("Failed to create custom field"),
  });
}

export function useUpdateDealCustomField() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: DealCustomFieldCreateRequest }) => dealCustomFieldApi.updateCustomField(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deal-custom-fields"] });
      toast.success("Custom field updated");
    },
    onError: () => toast.error("Failed to update custom field"),
  });
}

export function useDeleteDealCustomField() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => dealCustomFieldApi.deleteCustomField(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deal-custom-fields"] });
      toast.success("Custom field deleted");
    },
    onError: () => toast.error("Failed to delete custom field"),
  });
}
