"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { accountApi } from "@/lib/api/accounts";
import {
  AccountCreateRequest,
  AccountListParams,
  AccountUpdateRequest,
  Customer360Params,
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

/**
 * L3.2-P4: the Customer 360 workspace.
 *
 * <p>Every section is paged independently, so all six page/size pairs belong in the
 * query key. Advancing the timeline then refetches a different key than advancing
 * contacts would, which is what keeps the two from colliding.
 */
export function useCustomer360(id: string | undefined, params: Customer360Params = {}) {
  return useQuery({
    queryKey: ["accounts", id, "customer360", params],
    queryFn: () => accountApi.getCustomer360(id!, params),
    enabled: !!id,
  });
}

export function useAccountContacts(accountId: string | undefined, params: { page?: number; size?: number } = {}) {  return useQuery({
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
    onError: (error: { response?: { data?: { error?: { message?: string } } } }) =>
      toast.error(error?.response?.data?.error?.message || "Failed to create account"),
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
    onError: (error: { response?: { data?: { error?: { message?: string } } } }) =>
      toast.error(error?.response?.data?.error?.message || "Failed to update account"),
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
