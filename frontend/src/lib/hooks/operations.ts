"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { operationsApi } from "@/lib/api/operations";
import { apiErrorMessage } from "@/lib/api/api-utils";
import type { DeadLetterView, OutboxDeadEventView } from "@/types/operations";

/**
 * Query keys follow the project convention of a key factory plus coarse prefix
 * invalidation, so a recovery action refreshes the list it came from.
 */
export const operationsKeys = {
  deadLetters: (limit: number) => ["operations", "dlq", limit] as const,
  deadOutbox: (page: number, size: number) => ["operations", "outbox-dead", page, size] as const,
  all: ["operations"] as const,
};

const UNAVAILABLE = "RabbitMQ DLQ inspection is unavailable";

export function useDeadLetters(limit = 20) {
  return useQuery<{ data: DeadLetterView[]; meta: any }>({
    queryKey: operationsKeys.deadLetters(limit),
    queryFn: () => operationsApi.listDeadLetters(limit),
    // A dead letter queue is inspected deliberately, never polled. Refetch on focus
    // would rotate the queue head on every window switch for no benefit.
    refetchOnWindowFocus: false,
    retry: false,
  });
}

export function useRedriveDeadLetter() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (messageId: string) => operationsApi.redriveDeadLetter(messageId),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: operationsKeys.all });
      toast.success(
        `Re-driven to ${result.exchange} with routing key ${result.routingKey}. ` +
          `The original dead letter is still in the queue and is now marked as already re-driven.`
      );
    },
    onError: (error) => {
      toast.error(apiErrorMessage(error, "Re-drive failed. The dead letter was left untouched."));
    },
  });
}

export function useDeadOutboxEvents(page = 0, size = 20) {
  return useQuery<{ data: OutboxDeadEventView[]; meta: any }>({
    queryKey: operationsKeys.deadOutbox(page, size),
    queryFn: () => operationsApi.listDeadOutboxEvents(page, size),
    refetchOnWindowFocus: false,
  });
}

export function useRequeueDeadOutboxEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (eventId: string) => operationsApi.requeueDeadOutboxEvent(eventId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: operationsKeys.all });
      toast.success("Requeued. The normal outbox publisher will process it on its next poll.");
    },
    onError: (error) => {
      toast.error(
        apiErrorMessage(error, "Requeue failed. The event may have been requeued by another operator.")
      );
    },
  });
}

export { UNAVAILABLE };
