/**
 * L3.1 Ticket wire contract.
 *
 * Mirrors the backend DTOs exactly. Nothing here is writable unless the backend
 * accepts it — notably the Ticket PATCH does NOT change ownership, so
 * `TicketUpdateRequest` intentionally omits `ownerId` even though
 * `TicketResponse` carries it.
 */

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

export type TicketMessageVisibility = 'INTERNAL' | 'CUSTOMER_VISIBLE';

/**
 * Transitions the backend accepts. Mirrors TicketStatus.allowedTransitions so the
 * UI can offer only meaningful actions. The backend remains authoritative — this is
 * a UX affordance, not a second state machine.
 */
export const TICKET_STATUS_TRANSITIONS: Record<TicketStatus, TicketStatus[]> = {
  OPEN: ['IN_PROGRESS', 'RESOLVED'],
  IN_PROGRESS: ['RESOLVED'],
  RESOLVED: ['CLOSED', 'OPEN'],
  CLOSED: ['OPEN'],
};

export interface TicketResponse {
  id: string;
  tenantId: string;
  ownerId: string | null;
  accountId: string;
  contactId: string | null;
  subject: string;
  description: string | null;
  status: TicketStatus;
  resolvedAt: string | null;
  closedAt: string | null;
  responseDueAt: string | null;
  resolutionDueAt: string | null;
  firstResponseAt: string | null;
  slaBreached: boolean | null;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TicketCreateRequest {
  accountId: string;
  subject: string;
  description?: string;
  contactId?: string;
  ownerId?: string;
}

export interface TicketUpdateRequest {
  subject?: string;
  description?: string;
  accountId?: string;
  contactId?: string;
}

export interface TicketAssignRequest {
  ownerId: string;
}

export interface TicketStatusChangeRequest {
  status: TicketStatus;
}

export interface TicketListParams {
  accountId?: string;
  status?: TicketStatus;
  search?: string;
  page?: number;
  size?: number;
}

export interface TicketListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface TicketMessageResponse {
  id: string;
  tenantId: string;
  ticketId: string;
  authorUserId: string;
  body: string;
  visibility: TicketMessageVisibility;
  createdAt: string;
  updatedAt: string;
}

export interface TicketMessageCreateRequest {
  body: string;
  visibility?: TicketMessageVisibility;
}

export interface TicketMessageUpdateRequest {
  body?: string;
  visibility?: TicketMessageVisibility;
}

/**
 * Activity entry as returned by GET /tickets/{id}/activities.
 *
 * `performedBy` is null for SYSTEM entries (SLA breach, escalation), so `actorType`
 * is the authoritative actor indicator and `actorType === 'USER'` is the only case
 * where `performedBy` should be resolved to a name.
 */
export interface TicketActivityResponse {
  id: string;
  entityType: string;
  entityId: string;
  activityType: string;
  description: string | null;
  performedBy: string | null;
  actorType: string | null;
  actorSource: string | null;
  metadata: Record<string, unknown> | null;
  createdAt: string;
}

/** Known Ticket activity types. Unknown values are rendered generically, not dropped. */
export type TicketActivityType =
  | 'TICKET_CREATED'
  | 'TICKET_UPDATED'
  | 'TICKET_STATUS_CHANGED'
  | 'TICKET_OWNER_CHANGED'
  | 'TICKET_MESSAGE_CREATED'
  | 'TICKET_MESSAGE_UPDATED'
  | 'TICKET_MESSAGE_DELETED'
  | 'TICKET_SLA_BREACHED'
  | 'TICKET_ESCALATED'
  | 'TICKET_DELETED';

export interface TicketActivityMeta {
  ticketId?: string;
  accountId?: string;
  contactId?: string;
  ownerId?: string;
  status?: string;
  previousStatus?: string;
  newStatus?: string;
  previousOwnerId?: string | null;
  newOwnerId?: string;
  messageId?: string;
  authorUserId?: string;
  visibility?: string;
  reason?: string;
  breachType?: string;
  changedFields?: string[];
}
