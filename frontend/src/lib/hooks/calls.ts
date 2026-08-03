import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { callApi, type CallListParams } from '@/lib/api/calls';
import type { CallCreateRequest, CallDispositionRequest, CallLinkRequest, CallUpdateRequest } from '@/types/calls';

export function useCalls(params?: CallListParams) {
  return useQuery({
    queryKey: ['calls', params],
    queryFn: () => callApi.listCalls(params),
  });
}

export function useCall(id: string) {
  return useQuery({
    queryKey: ['call', id],
    queryFn: () => callApi.getCall(id),
    enabled: !!id,
    refetchInterval: (query) => {
      const data = query.state.data;
      if (!data) return 4000; // poll while loading initial
      if (data.status === 'HELD' || data.status === 'CANCELLED' || data.status === 'NOT_HELD' || data.endTime) {
        return false;
      }
      return 4000;
    },    
  });
}

export function useCreateCall() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CallCreateRequest) => callApi.createCall(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calls'] });
    },
  });
}

export function useUpdateCall() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: CallUpdateRequest }) =>
      callApi.updateCall(id, request),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['calls'] });
      queryClient.invalidateQueries({ queryKey: ['call', id] });
    },
  });
}

export function useLinkCallEntity() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: CallLinkRequest }) =>
      callApi.linkCallEntity(id, request),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['calls'] });
      queryClient.invalidateQueries({ queryKey: ['call', id] });
    },
  });
}

export function useSaveCallDisposition() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      request,
    }: {
      id: string;
      request: CallDispositionRequest;
    }) => callApi.saveDisposition(id, request),

    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({
        queryKey: ['calls'],
      });

      queryClient.invalidateQueries({
        queryKey: ['call', id],
      });
    },
  });
}

export function useDeleteCall() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => callApi.deleteCall(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calls'] });
    },
  });
}
