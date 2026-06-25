import { z } from "zod";

export const accountFormSchema = z.object({
  name: z.string().min(1, "Account name is required"),
  website: z.string().optional(),
  industry: z.string().optional(),
  phone: z.string().optional(),
  email: z.string().email("Invalid email").optional().or(z.literal("")),
  annualRevenue: z.coerce.number().nonnegative().optional(),
  employeeCount: z.coerce.number().nonnegative().optional(),
  description: z.string().optional(),
  country: z.string().optional(),
  state: z.string().optional(),
  city: z.string().optional(),
  addressLine1: z.string().optional(),
  postalCode: z.string().optional(),
  ownerUserId: z.string().optional(),
});

export type AccountFormData = z.infer<typeof accountFormSchema>;
