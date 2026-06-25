import { z } from "zod";

export const contactFormSchema = z.object({
  accountId: z.string().min(1, "Account is required"),
  firstName: z.string().min(1, "First name is required"),
  lastName: z.string().optional(),
  email: z.string().email("Invalid email").optional().or(z.literal("")),
  phone: z.string().optional(),
  title: z.string().optional(),
  department: z.string().optional(),
  ownerUserId: z.string().optional(),
});

export type ContactFormData = z.infer<typeof contactFormSchema>;
