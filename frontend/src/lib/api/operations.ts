import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import { unwrapResponse, unwrapListResponse } from "./api-utils";
import type {
  DeadLetterView,
  OperatorActionView,
  OutboxDeadEventView,
  RedriveResult,
} from "@/types/operations";

/**
 * OPS-01 / OPS-02 operator recovery tooling.
 *
 * Every call here is an operator action against failed asynchronous work. There is
 * no listing endpoint that triggers a side effect, and the recovery calls are
 * explicit POSTs the UI gates behind a confirmation dialog.
 */
export const operationsApi = {
  // ── OPS-01: RabbitMQ dead letters ──────────────────────────────────────────
  // Inspection only. The backend never consumes a message for these calls.
  listDeadLetters: async (limit?: number) => {
    const response = await api.get<ApiResponse<DeadLetterView[]>>(
      "/operations/rabbitmq/dlq",
      { params: { limit } }
    );
    return unwrapListResponse<DeadLetterView>(response);
  },

  getDeadLetter: async (messageId: string) => {
    const response = await api.get<ApiResponse<DeadLetterView>>(
      `/operations/rabbitmq/dlq/${encodeURIComponent(messageId)}`
    );
    return unwrapResponse(response);
  },

  /**
   * Republishes a copy to the original destination. This can cause the downstream
   * workflow to execute again - the UI must confirm before calling it.
   */
  redriveDeadLetter: async (messageId: string) => {
    const response = await api.post<ApiResponse<RedriveResult>>(
      `/operations/rabbitmq/dlq/${encodeURIComponent(messageId)}/redrive`
    );
    return unwrapResponse(response);
  },

  // ── OPS-02: terminal outbox events ─────────────────────────────────────────
  listDeadOutboxEvents: async (page = 0, size = 20) => {
    const response = await api.get<ApiResponse<OutboxDeadEventView[]>>(
      "/operations/outbox/dead",
      { params: { page, size } }
    );
    return unwrapListResponse<OutboxDeadEventView>(response);
  },

  getDeadOutboxEvent: async (eventId: string) => {
    const response = await api.get<ApiResponse<OutboxDeadEventView>>(
      `/operations/outbox/dead/${eventId}`
    );
    return unwrapResponse(response);
  },

  /**
   * Returns the event to the retryable state. The normal outbox publisher picks it
   * up; the UI must confirm before calling it.
   */
  requeueDeadOutboxEvent: async (eventId: string) => {
    const response = await api.post<ApiResponse<OutboxDeadEventView>>(
      `/operations/outbox/dead/${eventId}/requeue`
    );
    return unwrapResponse(response);
  },

  // ── shared audit view ─────────────────────────────────────────────────────
  listOperatorActions: async (actionType: "DLQ_REDRIVE" | "OUTBOX_REQUEUE", page = 0, size = 20) => {
    const response = await api.get<ApiResponse<OperatorActionView[]>>("/operations/actions", {
      params: { actionType, page, size },
    });
    return unwrapListResponse<OperatorActionView>(response);
  },
};
