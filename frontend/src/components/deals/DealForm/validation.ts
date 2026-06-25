import { z } from "zod";
import { ForecastCategory, RecordCategory } from "@/types/deal-stages";
import { DealType } from "@/types/deals";

const forecastCategoryEnum = z.enum(["PIPELINE", "BEST_CASE", "COMMIT", "CLOSED", "OMITTED"] as const satisfies readonly ForecastCategory[]);
const dealTypeEnum = z.enum(["NEW_BUSINESS", "EXISTING_BUSINESS", "RENEWAL", "UPSELL", "CROSS_SELL"] as const satisfies readonly DealType[]);

export const dealFormSchema = z.object({
  name: z.string().min(1, "Name is required"),
  amount: z.coerce.number().optional().nullable(),
  currency: z.string().optional().nullable(),
  stageId: z.string().min(1, "Stage is required"),
  accountId: z.string().optional().nullable(),
  contactId: z.string().optional().nullable(),
  ownerUserId: z.string().optional().nullable(),
  expectedCloseDate: z.string().optional().nullable(),
  probability: z.coerce.number().min(0).max(100).optional().nullable(),
  nextStep: z.string().optional().nullable(),
  forecastCategory: forecastCategoryEnum.optional().nullable(),
  dealType: dealTypeEnum.optional().nullable(),
  leadSource: z.string().optional().nullable(),
  campaignSource: z.string().optional().nullable(),
  closedDate: z.string().optional().nullable(),
  wonReason: z.string().optional().nullable(),
  lostReason: z.string().optional().nullable(),
  customData: z.record(z.string(), z.unknown()).optional(),
});

export type DealFormData = z.infer<typeof dealFormSchema>;
