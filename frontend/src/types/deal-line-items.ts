/**
 * L3.0-P5: DealLineItem wire contract.
 *
 * These mirror the backend DTO exactly. The previous shape used invented names
 * (`lineName`, `lineDescription`, `discountPercent`, `taxPercent`, `startDate`,
 * `endDate`, `offeringName`, `offeringCode`) that the backend has never had, so
 * every one of them arrived empty and every percentage became a silent zero.
 *
 * Money is amount-based, never percentage: `discountAmount` and `taxAmount`.
 * `currencyCode` is read-only and comes from the Offering.
 */
export interface DealLineItemResponse {
  id: string;
  dealId: string;
  offeringId?: string | null;
  itemName?: string | null;
  itemCode?: string | null;
  description?: string | null;
  quantity: number;
  unitPrice?: number | null;
  discountAmount?: number | null;
  taxAmount?: number | null;
  currencyCode?: string | null;
  lineTotal?: number | null;
  serviceStartDate?: string | null;
  serviceEndDate?: string | null;
  renewable?: boolean | null;
  renewalNoticeDays?: number | null;
  customData?: Record<string, unknown> | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface DealLineItemCreateRequest {
  offeringId?: string | null;
  quantity: number;
  unitPrice?: number | null;
  discountAmount?: number | null;
  taxAmount?: number | null;
  serviceStartDate?: string | null;
  serviceEndDate?: string | null;
  renewable?: boolean | null;
  renewalNoticeDays?: number | null;
  description?: string | null;
  customData?: Record<string, unknown> | null;
}

export interface DealLineItemUpdateRequest {
  quantity?: number;
  unitPrice?: number | null;
  discountAmount?: number | null;
  taxAmount?: number | null;
  serviceStartDate?: string | null;
  serviceEndDate?: string | null;
  renewable?: boolean | null;
  renewalNoticeDays?: number | null;
  description?: string | null;
  customData?: Record<string, unknown> | null;
}
