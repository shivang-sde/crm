import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ticketApi } from '@/lib/api/tickets';
import type {
  TicketActivityResponse,
  TicketAssignRequest,
  TicketCreateRequest,
  TicketListParams,
  TicketMessageCreateRequest,
  TicketMessageResponse,
  TicketMessageUpdateRequest,
  TicketResponse,
  TicketStatusChangeRequest,
  TicketUpdateRequest,
} from '@/types/tickets';

const invalidateTicketViews = (queryClient: ReturnType<typeof useQueryClient>, id?: string) => {
  queryClient.invalidateQueries({ queryKey: ['tickets'] });
  if (id) {
    queryClient.invalidateQueries({ queryKey: ['ticket', id] });
    // Any mutation changes the timeline, so it is refetched rather than patched.
    queryClient.invalidateQueries({ queryKey: ['ticketActivities', id] });
    queryClient.invalidateQueries({ queryKey: ['ticketMessages', id] });
  }
};

export function useTickets(params?: TicketListParams, enabled = true) {
  return useQuery({
    queryKey: ['tickets', params],
    queryFn: () => ticketApi.listTickets(params),
    enabled,
  });
}

export function useTicket(id: string) {
  return useQuery({
    queryKey: ['ticket', id],
    queryFn: () => ticketApi.getTicket(id),
    enabled: !!id,
  });
}

export function useCreateTicket() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: TicketCreateRequest) => ticketApi.createTicket(request),
    onSuccess: () => invalidateTicketViews(queryClient),
  });
}

export function useUpdateTicket() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: TicketUpdateRequest }) =>
      ticketApi.updateTicket(id, request),
    onSuccess: (_, { id }) => invalidateTicketViews(queryClient, id),
  });
}

export function useChangeTicketStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: TicketStatusChangeRequest }) =>
      ticketApi.changeTicketStatus(id, request),
    // Refetched, never patched locally: the backend owns the transition rules.
    onSuccess: (_, { id }) => invalidateTicketViews(queryClient, id),
  });
}

export function useAssignTicket() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: TicketAssignRequest }) =>
      ticketApi.assignTicket(id, request),
    onSuccess: (_, { id }) => invalidateTicketViews(queryClient, id),
  });
}

export function useDeleteTicket() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => ticketApi.deleteTicket(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tickets'] }),
  });
}

export function useTicketMessages(id: string, params: { page?: number; size?: number } = {}) {
  return useQuery({
    queryKey: ['ticketMessages', id, params],
    queryFn: () => ticketApi.listTicketMessages(id, params),
    enabled: !!id,
  });
}

export function useCreateTicketMessage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: TicketMessageCreateRequest }) =>
      ticketApi.createTicketMessage(id, request),
    onSuccess: (_, { id }) => invalidateTicketViews(queryClient, id),
  });
}

export function useUpdateTicketMessage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      messageId,
      request,
    }: {
      id: string;
      messageId: string;
      request: TicketMessageUpdateRequest;
    }) => ticketApi.updateTicketMessage(id, messageId, request),
    onSuccess: (_, { id }) => invalidateTicketViews(queryClient, id),
  });
}

export function useDeleteTicketMessage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, messageId }: { id: string; messageId: string }) =>
      ticketApi.deleteTicketMessage(id, messageId),
    onSuccess: (_, { id }) => invalidateTicketViews(queryClient, id),
  });
}

export function useTicketActivities(id: string, params: { page?: number; size?: number } = {}) {
  return useQuery({
    queryKey: ['ticketActivities', id, params],
    queryFn: () => ticketApi.listTicketActivities(id, params),
    enabled: !!id,
  });
}

export type {
  TicketActivityResponse,
  TicketMessageResponse,
  TicketResponse,
};
