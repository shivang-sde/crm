export interface DealLineItemResponse {
  id: string;
  tenantId?: string;
  dealId: string;
  offeringId?: string | null;
  offeringName?: string | null;
  offeringCode?: string | null;
  lineName: string;
  lineDescription?: string | null;
  quantity: number;
  unitPrice: number;
  discountPercent: number;
  taxPercent: number;
  startDate?: string | null;
  endDate?: string | null;
  renewable?: boolean | null;
  customData?: Record<string, unknown>;
  lineTotal?: number | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface DealLineItemCreateRequest {
  offeringId?: string | null;
  lineName: string;
  lineDescription?: string | null;
  quantity: number;
  unitPrice: number;
  discountPercent?: number;
  taxPercent?: number;
  startDate?: string | null;
  endDate?: string | null;
  renewable?: boolean;
  customData?: Record<string, unknown>;
}

export interface DealLineItemUpdateRequest {
  offeringId?: string | null;
  lineName?: string;
  lineDescription?: string | null;
  quantity?: number;
  unitPrice?: number;
  discountPercent?: number;
  taxPercent?: number;
  startDate?: string | null;
  endDate?: string | null;
  renewable?: boolean;
  customData?: Record<string, unknown>;
}
