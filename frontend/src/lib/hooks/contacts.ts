"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { contactApi } from "@/lib/api/contacts";
import { ContactCreateRequest, ContactListParams, ContactUpdateRequest } from "@/types/contacts";

export function useContacts(params: ContactListParams = {}) {
  return useQuery({
    queryKey: ["contacts", params],
    queryFn: () => contactApi.listContacts(params),
  });
}

export function useContact(id: string | undefined) {
  return useQuery({
    queryKey: ["contacts", id],
    queryFn: () => contactApi.getContact(id!),
    enabled: !!id,
  });
}

export function useContactActivities(contactId: string | undefined) {
  return useQuery({
    queryKey: ["contacts", contactId, "activities"],
    queryFn: () => contactApi.getActivities(contactId!),
    enabled: !!contactId,
  });
}

export function useSearchContacts(query: string) {
  return useQuery({
    queryKey: ["contacts", "search", query],
    queryFn: () => contactApi.searchContacts(query),
    enabled: query.trim().length > 0,
  });
}

export function useContactNotes(contactId: string | undefined) {
  return useQuery({
    queryKey: ["contacts", contactId, "notes"],
    queryFn: () => contactApi.getNotes(contactId!),
    enabled: !!contactId,
  });
}

export function useAddContactNote() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ contactId, note }: { contactId: string; note: string }) =>
      contactApi.addNote(contactId, note),
    onSuccess: (_, { contactId }) => {
      queryClient.invalidateQueries({ queryKey: ["contacts", contactId, "notes"] });
      queryClient.invalidateQueries({ queryKey: ["contacts", contactId, "activities"] });
      toast.success("Note added");
    },
    onError: () => toast.error("Failed to add note"),
  });
}

export function useDeleteContactNote() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ contactId, noteId }: { contactId: string; noteId: string }) =>
      contactApi.deleteNote(contactId, noteId),
    onSuccess: (_, { contactId }) => {
      queryClient.invalidateQueries({ queryKey: ["contacts", contactId, "notes"] });
      toast.success("Note deleted");
    },
    onError: () => toast.error("Failed to delete note"),
  });
}

export function useCreateContact() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: ContactCreateRequest) => contactApi.createContact(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["contacts"] });
      toast.success("Contact created successfully");
    },
    onError: () => toast.error("Failed to create contact"),
  });
}

export function useUpdateContact() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: ContactUpdateRequest }) =>
      contactApi.updateContact(id, data),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["contacts"] });
      queryClient.invalidateQueries({ queryKey: ["contacts", id] });
      toast.success("Contact updated successfully");
    },
    onError: () => toast.error("Failed to update contact"),
  });
}

export function useDeleteContact() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => contactApi.deleteContact(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["contacts"] });
      toast.success("Contact deleted successfully");
    },
    onError: () => toast.error("Failed to delete contact"),
  });
}
