import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { formsApi } from "@/lib/api/forms";
import type { FormCreateRequest, FormUpdateRequest } from "@/types/forms";

export const formsKeys = {
  all: ["forms"] as const,
  list: () => [...formsKeys.all, "list"] as const,
  detail: (id: string) => [...formsKeys.all, "detail", id] as const,
};

export function useForms() {
  return useQuery({
    queryKey: formsKeys.list(),
    queryFn: () => formsApi.list(),
  });
}

export function useForm(id?: string) {
  return useQuery({
    queryKey: formsKeys.detail(id ?? ""),
    queryFn: () => formsApi.get(id ?? ""),
    enabled: Boolean(id),
  });
}

export function useCreateForm() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: FormCreateRequest) => formsApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: formsKeys.list() }),
  });
}

export function useUpdateForm() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: FormUpdateRequest }) => formsApi.update(id, data),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: formsKeys.list() });
      qc.invalidateQueries({ queryKey: formsKeys.detail(vars.id) });
    },
  });
}

export function useDeleteForm() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => formsApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: formsKeys.list() }),
  });
}

export function usePublishForm() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => formsApi.publish(id),
    onSuccess: (_data, id) => {
      qc.invalidateQueries({ queryKey: formsKeys.list() });
      qc.invalidateQueries({ queryKey: formsKeys.detail(id) });
    },
  });
}

export function useUnpublishForm() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => formsApi.unpublish(id),
    onSuccess: (_data, id) => {
      qc.invalidateQueries({ queryKey: formsKeys.list() });
      qc.invalidateQueries({ queryKey: formsKeys.detail(id) });
    },
  });
}

export function useDuplicateForm() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => formsApi.duplicate(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: formsKeys.list() }),
  });
}
