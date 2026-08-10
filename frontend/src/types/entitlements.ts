import { z } from "zod";

export type EntitlementStatus =
  | "PENDING"
  | "ACTIVE"
  | "SUSPENDED"
  | "EXPIRED"
  | "CANCELLED"
  | "RENEWED"
  | "TERMINATED";

export interface CustomerEntitlementResponse {
  id: string;
  tenant_id?: string | null;
  account_id?: string | null;
  contact_id?: string | null;
  offering_id?: string | null;
  deal_id?: string | null;
  deal_line_item_id?: string | null;
  name?: string | null;
  code?: string | null;
  description?: string | null;
  status?: EntitlementStatus | null;
  start_date?: string | null;
  end_date?: string | null;
  quantity?: number | null;
  agreed_price?: number | null;
  currency_code?: string | null;
  renewable?: boolean | null;
  auto_renew?: boolean | null;
  renewal_notice_days?: number | null;
  renewal_due_date?: string | null;
  renewed_from_entitlement_id?: string | null;
  renewed_to_entitlement_id?: string | null;
  renewal_deal_id?: string | null;
  custom_data?: Record<string, unknown> | null;
  owner_user_id?: string | null;
  created_at?: string | null;
  updated_at?: string | null;
}

export interface CustomerEntitlementListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface CustomerEntitlementListParams {
  accountId?: string;
  contactId?: string;
  offeringId?: string;
  status?: EntitlementStatus;
  ownerUserId?: string;
  renewable?: boolean;
  endDateFrom?: string;
  endDateTo?: string;
  search?: string;
  page?: number;
  size?: number;
}

export interface CustomerEntitlementUpdateRequest {
  description?: string | null;
  quantity?: number | null;
  start_date?: string | null;
  end_date?: string | null;
  renewable?: boolean | null;
  auto_renew?: boolean | null;
  renewal_notice_days?: number | null;
  owner_user_id?: string | null;
  custom_data?: Record<string, unknown> | null;
}

const optionalNumericField = z
  .union([z.number(), z.string()])
  .optional()
  .transform((value) => {
    if (value === undefined || value === "") return undefined;
    const parsed = typeof value === "number" ? value : Number(value);
    return Number.isNaN(parsed) ? undefined : parsed;
  });

export const entitlementUpdateSchema = z.object({
  description: z.string().optional(),
  quantity: optionalNumericField,
  startDate: z.string().optional(),
  endDate: z.string().optional(),
  renewable: z.boolean().optional(),
  autoRenew: z.boolean().optional(),
  renewalNoticeDays: optionalNumericField,
  ownerUserId: z.string().optional(),
});

export type EntitlementUpdateFormValues = z.infer<typeof entitlementUpdateSchema>;
