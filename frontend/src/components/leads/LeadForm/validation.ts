import { z } from "zod";

export const leadFormSchema = z.object({
  firstName: z.string().min(1, "First name is required"),
  lastName: z.string().optional(),
  email: z
    .string()
    .email("Invalid email")
    .optional()
    .or(z.literal("")),
  phone: z.string().optional(),
  company: z.string().optional(),
  statusId: z.string().min(1, "Status is required"),
  sourceId: z.string().optional(),
  ownerUserId: z.string().optional(),
  score: z.coerce.number().min(0).max(100).optional(),
  notes: z.string().optional(),
  customData: z.record(z.string(), z.unknown()).optional(),
});

export type LeadFormData = z.infer<typeof leadFormSchema>;
