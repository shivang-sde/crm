"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { accountApi } from "@/lib/api/accounts";
import {
  AccountCreateRequest,
  AccountListParams,
  AccountUpdateRequest,
} from "@/types/accounts";

export function useAccounts(params: AccountListParams = {}) {
  return useQuery({
    queryKey: ["accounts", params],
    queryFn: () => accountApi.listAccounts(params),
  });
}

export function useAccount(id: string | undefined) {
  return useQuery({
    queryKey: ["accounts", id],
    queryFn: () => accountApi.getAccount(id!),
    enabled: !!id,
  });
}

export function useAccountContacts(accountId: string | undefined, params: { page?: number; size?: number } = {}) {
  return useQuery({
    queryKey: ["accounts", accountId, "contacts", params],
    queryFn: () => accountApi.getAccountContacts(accountId!, params),
    enabled: !!accountId,
  });
}

export function useAccountActivities(accountId: string | undefined) {
  return useQuery({
    queryKey: ["accounts", accountId, "activities"],
    queryFn: () => accountApi.getActivities(accountId!),
    enabled: !!accountId,
  });
}

export function useSearchAccounts(query: string) {
  return useQuery({
    queryKey: ["accounts", "search", query],
    queryFn: () => accountApi.searchAccounts(query),
    enabled: query.trim().length > 0,
  });
}

export function useAccountNotes(accountId: string | undefined) {
  return useQuery({
    queryKey: ["accounts", accountId, "notes"],
    queryFn: () => accountApi.getNotes(accountId!),
    enabled: !!accountId,
  });
}

export function useAddAccountNote() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ accountId, note }: { accountId: string; note: string }) =>
      accountApi.addNote(accountId, note),
    onSuccess: (_, { accountId }) => {
      queryClient.invalidateQueries({ queryKey: ["accounts", accountId, "notes"] });
      queryClient.invalidateQueries({ queryKey: ["accounts", accountId, "activities"] });
      toast.success("Note added");
    },
    onError: () => toast.error("Failed to add note"),
  });
}

export function useDeleteAccountNote() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ accountId, noteId }: { accountId: string; noteId: string }) =>
      accountApi.deleteNote(accountId, noteId),
    onSuccess: (_, { accountId }) => {
      queryClient.invalidateQueries({ queryKey: ["accounts", accountId, "notes"] });
      toast.success("Note deleted");
    },
    onError: () => toast.error("Failed to delete note"),
  });
}

export function useCreateAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: AccountCreateRequest) => accountApi.createAccount(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
      toast.success("Account created successfully");
    },
    onError: () => toast.error("Failed to create account"),
  });
}

export function useUpdateAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: AccountUpdateRequest }) =>
      accountApi.updateAccount(id, data),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
      queryClient.invalidateQueries({ queryKey: ["accounts", id] });
      toast.success("Account updated successfully");
    },
    onError: () => toast.error("Failed to update account"),
  });
}

export function useDeleteAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => accountApi.deleteAccount(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
      toast.success("Account deleted successfully");
    },
    onError: () => toast.error("Failed to delete account"),
  });
}
