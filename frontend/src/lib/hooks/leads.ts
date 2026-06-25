"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { leadApi } from "@/lib/api/leads";
import {
  LeadConvertRequest,
  LeadCreateRequest,
  LeadCustomFieldCreateRequest,
  LeadListParams,
  LeadSourceCreateRequest,
  LeadStatusCreateRequest,
  LeadUpdateRequest,
} from "@/types/leads";

export function useLeads(params: LeadListParams = {}) {
  return useQuery({
    queryKey: ["leads", params],
    queryFn: () => leadApi.listLeads(params),
  });
}

export function useLead(id: string | undefined) {
  return useQuery({
    queryKey: ["leads", id],
    queryFn: () => leadApi.getLead(id!),
    enabled: !!id,
  });
}

export function useLeadStatuses() {
  return useQuery({
    queryKey: ["lead-statuses"],
    queryFn: () => leadApi.listStatuses(),
  });
}

export function useLeadSources() {
  return useQuery({
    queryKey: ["lead-sources"],
    queryFn: () => leadApi.listSources(),
  });
}

export function useLeadCustomFields() {
  return useQuery({
    queryKey: ["lead-custom-fields"],
    queryFn: () => leadApi.listCustomFields(),
  });
}

export function useLeadActivities(leadId: string | undefined) {
  return useQuery({
    queryKey: ["lead-activities", leadId],
    queryFn: () => leadApi.getActivities(leadId!),
    enabled: !!leadId,
  });
}

export function useLeadNotes(leadId: string | undefined) {
  return useQuery({
    queryKey: ["lead-notes", leadId],
    queryFn: () => leadApi.getNotes(leadId!),
    enabled: !!leadId,
  });
}

export function useCreateLead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: LeadCreateRequest) => leadApi.createLead(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["leads"] });
      toast.success("Lead created successfully");
    },
    onError: (error: { response?: { data?: { error?: { message?: string } } } }) => {
      toast.error(error?.response?.data?.error?.message || "Failed to create lead");
    },
  });
}

export function useUpdateLead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: LeadUpdateRequest }) =>
      leadApi.updateLead(id, data),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["leads"] });
      queryClient.invalidateQueries({ queryKey: ["leads", id] });
      toast.success("Lead updated successfully");
    },
    onError: () => toast.error("Failed to update lead"),
  });
}

export function useDeleteLead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => leadApi.deleteLead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["leads"] });
      toast.success("Lead deleted successfully");
    },
    onError: () => toast.error("Failed to delete lead"),
  });
}

export function useConvertLead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: LeadConvertRequest }) =>
      leadApi.convertLead(id, payload),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["leads"] });
      queryClient.invalidateQueries({ queryKey: ["leads", id] });
      toast.success("Lead converted successfully");
    },
    onError: (error: { response?: { data?: { error?: { message?: string } } } }) =>
      toast.error(error?.response?.data?.error?.message || "Failed to convert lead"),
  });
}

export function useAssignLead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ownerUserId }: { id: string; ownerUserId: string }) =>
      leadApi.assignLead(id, ownerUserId),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["leads"] });
      queryClient.invalidateQueries({ queryKey: ["leads", id] });
      queryClient.invalidateQueries({ queryKey: ["lead-activities", id] });
      toast.success("Lead assigned successfully");
    },
    onError: () => toast.error("Failed to assign lead"),
  });
}

export function useChangeLeadStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, statusId }: { id: string; statusId: string }) =>
      leadApi.changeLeadStatus(id, statusId),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["leads"] });
      queryClient.invalidateQueries({ queryKey: ["leads", id] });
      queryClient.invalidateQueries({ queryKey: ["lead-activities", id] });
      toast.success("Lead status updated");
    },
    onError: () => toast.error("Failed to change lead status"),
  });
}

export function useAddLeadNote() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ leadId, note }: { leadId: string; note: string }) =>
      leadApi.addNote(leadId, note),
    onSuccess: (_, { leadId }) => {
      queryClient.invalidateQueries({ queryKey: ["lead-notes", leadId] });
      queryClient.invalidateQueries({ queryKey: ["lead-activities", leadId] });
      toast.success("Note added");
    },
    onError: () => toast.error("Failed to add note"),
  });
}

export function useDeleteLeadNote() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ leadId, noteId }: { leadId: string; noteId: string }) =>
      leadApi.deleteNote(leadId, noteId),
    onSuccess: (_, { leadId }) => {
      queryClient.invalidateQueries({ queryKey: ["lead-notes", leadId] });
      toast.success("Note deleted");
    },
    onError: () => toast.error("Failed to delete note"),
  });
}

export function useCreateLeadStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: LeadStatusCreateRequest) => leadApi.createStatus(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lead-statuses"] });
      toast.success("Status created");
    },
    onError: (error: { response?: { data?: { error?: { message?: string } } } }) => {
      toast.error(error?.response?.data?.error?.message || "Failed to create status");
    },
  });
}

export function useUpdateLeadStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: LeadStatusCreateRequest }) =>
      leadApi.updateStatus(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lead-statuses"] });
      toast.success("Status updated");
    },
    onError: (error: { response?: { data?: { error?: { message?: string } } } }) => {
      toast.error(error?.response?.data?.error?.message || "Failed to update status");
    },
  });
}

export function useDeleteLeadStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => leadApi.deleteStatus(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lead-statuses"] });
      toast.success("Status deleted");
    },
    onError: (error: { response?: { data?: { error?: { message?: string } } } }) => {
      toast.error(error?.response?.data?.error?.message || "Failed to delete status");
    },
  });
}

export function useCreateLeadSource() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: LeadSourceCreateRequest) => leadApi.createSource(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lead-sources"] });
      toast.success("Source created");
    },
    onError: (error: { response?: { data?: { error?: { message?: string } } } }) => {
      toast.error(error?.response?.data?.error?.message || "Failed to create source");
    },
  });
}

export function useUpdateLeadSource() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: LeadSourceCreateRequest }) =>
      leadApi.updateSource(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lead-sources"] });
      toast.success("Source updated");
    },
    onError: (error: { response?: { data?: { error?: { message?: string } } } }) => {
      toast.error(error?.response?.data?.error?.message || "Failed to update source");
    },
  });
}

export function useDeleteLeadSource() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => leadApi.deleteSource(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lead-sources"] });
      toast.success("Source deleted");
    },
    onError: (error: { response?: { data?: { error?: { message?: string } } } }) => {
      toast.error(error?.response?.data?.error?.message || "Failed to delete source");
    },
  });
}

export function useCreateLeadCustomField() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: LeadCustomFieldCreateRequest) => leadApi.createCustomField(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lead-custom-fields"] });
      toast.success("Custom field created");
    },
    onError: (error: { response?: { data?: { error?: { message?: string } } } }) => {
      toast.error(error?.response?.data?.error?.message || "Failed to create field");
    },
  });
}

export function useUpdateLeadCustomField() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: LeadCustomFieldCreateRequest }) =>
      leadApi.updateCustomField(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lead-custom-fields"] });
      toast.success("Custom field updated");
    },
    onError: (error: { response?: { data?: { error?: { message?: string } } } }) => {
      toast.error(error?.response?.data?.error?.message || "Failed to update field");
    },
  });
}

export function useDeleteLeadCustomField() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => leadApi.deleteCustomField(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lead-custom-fields"] });
      toast.success("Custom field deleted");
    },
    onError: (error: { response?: { data?: { error?: { message?: string } } } }) => {
      toast.error(error?.response?.data?.error?.message || "Failed to delete field");
    },
  });
}
