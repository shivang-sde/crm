import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { meetingApi, type MeetingListParams } from '@/lib/api/meetings';
import type { MeetingCreateRequest, MeetingUpdateRequest } from '@/types/meetings';

export function useMeetings(params?: MeetingListParams) {
  return useQuery({
    queryKey: ['meetings', params],
    queryFn: () => meetingApi.listMeetings(params),
  });
}

export function useMeeting(id: string) {
  return useQuery({
    queryKey: ['meeting', id],
    queryFn: () => meetingApi.getMeeting(id),
    enabled: !!id,
  });
}

export function useCreateMeeting() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: MeetingCreateRequest) => meetingApi.createMeeting(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['meetings'] });
    },
  });
}

export function useUpdateMeeting() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: MeetingUpdateRequest }) =>
      meetingApi.updateMeeting(id, request),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['meetings'] });
      queryClient.invalidateQueries({ queryKey: ['meeting', id] });
    },
  });
}

export function useDeleteMeeting() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => meetingApi.deleteMeeting(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['meetings'] });
    },
  });
}
