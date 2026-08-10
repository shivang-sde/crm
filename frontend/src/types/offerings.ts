import z from "zod";

export type OfferingType =
  | "PRODUCT"
  | "SERVICE"
  | "SUBSCRIPTION"
  | "LICENSE"
  | "MEMBERSHIP"
  | "WARRANTY"
  | "MAINTENANCE"
  | "RENTAL"
  | "OTHER";

export type BillingType = "ONE_TIME" | "RECURRING" | "USAGE_BASED" | "FREE";

export type BillingInterval =
  | "DAILY"
  | "WEEKLY"
  | "MONTHLY"
  | "QUARTERLY"
  | "HALF_YEARLY"
  | "YEARLY"
  | "CUSTOM";

export interface OfferingResponse {
  id: string;
  tenantId?: string;
  name: string;
  code: string;
  description?: string | null;
  offeringType?: OfferingType | null;
  billingType?: BillingType | null;
  billingInterval?: BillingInterval | null;
  defaultPrice?: number | null;
  currencyCode?: string | null;
  defaultTermDays?: number | null;
  renewable?: boolean | null;
  active?: boolean | null;
  ownerUserId?: string | null;
  ownerName?: string | null;
  customData?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface OfferingCreateRequest {
  name: string;
  code: string;
  description?: string | null;
  offeringType?: OfferingType | null;
  billingType?: BillingType | null;
  billingInterval?: BillingInterval | null;
  defaultPrice?: number | null;
  currencyCode?: string | null;
  defaultTermDays?: number | null;
  renewable?: boolean | null;
  active?: boolean | null;
  ownerUserId?: string | null;
  customData?: Record<string, unknown>;
}

export interface OfferingUpdateRequest {
  name?: string;
  code?: string;
  description?: string | null;
  offeringType?: OfferingType | null;
  billingType?: BillingType | null;
  billingInterval?: BillingInterval | null;
  defaultPrice?: number | null;
  currencyCode?: string | null;
  defaultTermDays?: number | null;
  renewable?: boolean | null;
  active?: boolean | null;
  ownerUserId?: string | null;
  customData?: Record<string, unknown>;
}

export interface OfferingListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface OfferingListParams {
  page?: number;
  size?: number;
  search?: string;
  offeringType?: OfferingType;
  billingType?: BillingType;
  active?: boolean;
  ownerUserId?: string;
}



export const offeringTypes = [
  "PRODUCT",
  "SERVICE",
  "SUBSCRIPTION",
  "LICENSE",
  "MEMBERSHIP",
  "WARRANTY",
  "MAINTENANCE",
  "RENTAL",
  "OTHER",
] as const satisfies readonly OfferingType[];

export const billingTypes = [
  "ONE_TIME",
  "RECURRING",
  "USAGE_BASED",
  "FREE",
] as const satisfies readonly BillingType[];

export const billingIntervals = [
  "DAILY",
  "WEEKLY",
  "MONTHLY",
  "QUARTERLY",
  "HALF_YEARLY",
  "YEARLY",
  "CUSTOM",
] as const satisfies readonly BillingInterval[];

const optionalNumber = z.preprocess(
  (value) => {
    if (value === "" || value === null || value === undefined) {
      return undefined;
    }

    return value;
  },
  z.coerce.number().nonnegative().optional(),
);

const optionalInteger = z.preprocess(
  (value) => {
    if (value === "" || value === null || value === undefined) {
      return undefined;
    }

    return value;
  },
  z.coerce.number().int().nonnegative().optional(),
);

export const schema = z.object({
  name: z.string().trim().min(1, "Name is required"),
  code: z.string().trim().min(1, "Code is required"),
  description: z.string().optional(),
  offeringType: z.enum(offeringTypes),
  billingType: z.enum(billingTypes),
  billingInterval: z.enum(billingIntervals).optional(),
  defaultPrice: optionalNumber,
  currencyCode: z
    .string()
    .trim()
    .length(3, "Currency code must contain 3 characters")
    .transform((value) => value.toUpperCase())
    .optional(),
  defaultTermDays: optionalInteger,
  renewable: z.boolean(),
  active: z.boolean(),
});

export type OfferingFormInput = z.input<typeof schema>;
export type OfferingFormOutput = z.output<typeof schema>;
