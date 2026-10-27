"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { apiErrorMessage } from "@/lib/api/api-utils";
import { feedbackApi } from "@/lib/api/feedback";
import type { FeedbackCreateRequest, FeedbackListParams } from "@/types/feedback";

export function useFeedback(params: FeedbackListParams = {}, enabled = true) {
  return useQuery({
    queryKey: ["feedback", params],
    queryFn: () => feedbackApi.listFeedback(params),
    enabled,
  });
}

export function useCreateFeedback() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: FeedbackCreateRequest) => feedbackApi.createFeedback(request),
    onSuccess: (_, request) => {
      queryClient.invalidateQueries({ queryKey: ["feedback"] });
      if (request.expectedAccountId) {
        queryClient.invalidateQueries({
          queryKey: ["accounts", request.expectedAccountId, "customer360"],
        });
      }
      toast.success("Feedback recorded");
    },
    onError: (error) => {
      toast.error(apiErrorMessage(error, "Failed to record feedback"));
    },
  });
}

interface ConvertComplaintVariables {
  feedbackId: string;
  accountId?: string;
}

export function useConvertComplaintToTicket() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ feedbackId }: ConvertComplaintVariables) =>
      feedbackApi.convertComplaintToTicket(feedbackId),
    onSuccess: (_, { accountId }) => {
      queryClient.invalidateQueries({ queryKey: ["feedback"] });
      queryClient.invalidateQueries({ queryKey: ["tickets"] });
      if (accountId) {
        queryClient.invalidateQueries({
          queryKey: ["accounts", accountId, "customer360"],
        });
      }
      toast.success("Ticket created from complaint");
    },
    onError: (error) => {
      toast.error(apiErrorMessage(error, "Failed to create ticket from complaint"));
    },
  });
}
