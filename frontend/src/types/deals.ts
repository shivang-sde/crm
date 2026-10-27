import { DealStageSummary, RecordCategory, ForecastCategory } from "./deal-stages";
import { DealCustomFieldResponse } from "./deal-custom-fields";
import { DealLineItemCreateRequest } from "./deal-line-items";

export type DealFieldType =
  | "TEXT"
  | "TEXTAREA"
  | "NUMBER"
  | "EMAIL"
  | "PHONE"
  | "DATE"
  | "BOOLEAN"
  | "SELECT"
  | "MULTISELECT"
  | "URL";

export type DealType = "NEW_BUSINESS" | "EXISTING_BUSINESS" | "RENEWAL" | "UPSELL" | "CROSS_SELL";

export interface DealResponse {
  id: string;
  name: string;
  amount?: number | null;
  stage: DealStageSummary;
  accountId?: string | null;
  contactId?: string | null;
  leadId?: string | null;
  ownerUserId?: string | null;
  expectedCloseDate?: string | null;
  probability?: number | null;
  isWon?: boolean;
  isLost?: boolean;
  recordCategory?: RecordCategory;
  expectedRevenue?: number | null;
  forecastCategory?: ForecastCategory | null;
  nextStep?: string | null;
  dealType?: DealType | null;
  leadSource?: string | null;
  campaignSource?: string | null;
  closedDate?: string | null;
  wonReason?: string | null;
  lostReason?: string | null;
  description?: string | null;
  customData?: Record<string, unknown>;
  createdBy?: string;
  updatedBy?: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * L3.0-P5: a Deal's initial line items, submitted with the Deal itself so that a
 * directly-created CLOSED_WON Deal is born with its coverage attached instead of
 * having to be opened up to be sold.
 *
 * Required and non-empty for a won stage; optional otherwise. Editing lines stays
 * on the line-item endpoints, so `DealUpdateRequest` deliberately has no
 * `lineItems`.
 */
export interface DealCreateRequest {
  name: string;
  amount?: number | null;
  stageId?: string;
  accountId?: string;
  contactId?: string;
  leadId?: string;
  ownerUserId?: string;
  expectedCloseDate?: string;
  closedDate?: string | null;
  probability?: number;
  forecastCategory?: ForecastCategory | null;
  nextStep?: string | null;
  dealType?: DealType | null;
  leadSource?: string | null;
  campaignSource?: string | null;
  wonReason?: string | null;
  lostReason?: string | null;
  description?: string | null;
  customData?: Record<string, unknown>;
  lineItems?: DealLineItemCreateRequest[];
}

export interface DealUpdateRequest {
  name?: string;
  amount?: number | null;
  stageId?: string;
  accountId?: string | null;
  contactId?: string | null;
  ownerUserId?: string | null;
  expectedCloseDate?: string | null;
  probability?: number | null;
  expectedRevenue?: number | null;
  forecastCategory?: ForecastCategory | null;
  nextStep?: string | null;
  dealType?: DealType | null;
  leadSource?: string | null;
  campaignSource?: string | null;
  closedDate?: string | null;
  wonReason?: string | null;
  lostReason?: string | null;
  customData?: Record<string, unknown>;
}

export interface DealActivityResponse {
  id: string;
  dealId: string;
  activityType: string;
  description?: string;
  performedBy?: string;
  metadata?: Record<string, unknown>;
  createdAt: string;
}

export interface DealNoteResponse {
  id: string;
  dealId: string;
  note: string;
  createdBy?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface DealListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface DealListParams {
  page?: number;
  size?: number;
  search?: string;
  stage?: string;
  accountId?: string;
  contactId?: string;
  owner?: string;
  isWon?: boolean;
  isLost?: boolean;
  closeDateFrom?: string;
  closeDateTo?: string;
}

export interface DealCustomFieldResponseExt extends DealCustomFieldResponse {}
