import { ContactResponse } from "./contacts";
import { DealResponse } from "./deals";
import { CustomerEntitlementResponse } from "./entitlements";
import { FeedbackResponse } from "./feedback";
import { TicketActivityResponse, TicketResponse } from "./tickets";

export interface AccountListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface AccountListParams {
  search?: string;
  owner?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface AccountCreateRequest {
  name: string;
  website?: string;
  industry?: string;
  phone?: string;
  email?: string;
  annualRevenue?: number;
  employeeCount?: number;
  description?: string;
  country?: string;
  state?: string;
  city?: string;
  addressLine1?: string;
  postalCode?: string;
  ownerUserId?: string;
  customData?: Record<string, unknown>;
}

export interface AccountUpdateRequest {
  name?: string;
  website?: string;
  industry?: string;
  phone?: string;
  email?: string;
  annualRevenue?: number;
  employeeCount?: number;
  description?: string;
  country?: string;
  state?: string;
  city?: string;
  addressLine1?: string;
  postalCode?: string;
  ownerUserId?: string;
  customData?: Record<string, unknown>;
}

export interface AccountResponse {
  id: string;
  name: string;
  website?: string;
  industry?: string;
  phone?: string;
  email?: string;
  annualRevenue?: number;
  employeeCount?: number;
  description?: string;
  country?: string;
  state?: string;
  city?: string;
  addressLine1?: string;
  postalCode?: string;
  ownerUserId?: string;
  leadId?: string;
  customData?: Record<string, unknown>;
  isActive?: boolean;
  createdAt: string;
  updatedAt: string;
}

// ---------------------------------------------------------------------------
// L3.2-P4: Customer 360 read contract
// ---------------------------------------------------------------------------

/**
 * One paginated child collection in the Customer 360 response.
 *
 * <p>Mirrors the backend `Customer360Section<T>` (L3.2-P2). A bare array cannot say
 * whether records were omitted, which is why the backend stopped returning one; the
 * frontend keeps the same shape rather than hiding the metadata behind an adapter.
 */
export interface Customer360Section<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
}

/**
 * A Customer 360 timeline entry.
 *
 * <p>Aliased from `TicketActivityResponse` rather than redeclared: both are the
 * backend's single `ActivityResponse` DTO, which `tests/ticket-contract.check.cjs`
 * already guards field-for-field. A second copy here would be free to drift.
 */
export type Customer360Activity = TicketActivityResponse;

/** Entity types the backend admits to a Customer 360 timeline (L3.2-P3). */
export type Customer360TimelineEntityType = "ACCOUNT" | "CONTACT" | "DEAL" | "TICKET" | "ENTITLEMENT";

/**
 * `GET /api/v1/accounts/{id}/360`.
 *
 * <p>Every collection is scoped and paged independently, and each honours the
 * caller's own record scope in its own domain. The backend has no partial-success
 * mode: a domain authorization failure fails the whole request, so there is no
 * per-section error field here to pretend otherwise.
 */
export interface Customer360Response {
  account: AccountResponse;
  contacts: Customer360Section<ContactResponse>;
  deals: Customer360Section<DealResponse>;
  entitlements: Customer360Section<CustomerEntitlementResponse>;
  tickets: Customer360Section<TicketResponse>;
  feedback: Customer360Section<FeedbackResponse>;
  timeline: Customer360Section<Customer360Activity>;
}

/**
 * Independent pagination for the six Customer 360 collections.
 *
 * <p>Names and defaults match `AccountController.getCustomer360` exactly: each
 * section defaults to page 0, size 20.
 */
export interface Customer360Params {
  contactsPage?: number;
  contactsSize?: number;
  dealsPage?: number;
  dealsSize?: number;
  entitlementsPage?: number;
  entitlementsSize?: number;
  ticketsPage?: number;
  ticketsSize?: number;
  feedbackPage?: number;
  feedbackSize?: number;
  timelinePage?: number;
  timelineSize?: number;
}
