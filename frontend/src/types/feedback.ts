/**
 * L3.3-P1 feedback wire contract.
 *
 * Mirrors the backend FeedbackResponse / FeedbackCreateRequest DTOs. The create
 * request identifies the customer by email; `expectedAccountId` is only a staff-UI
 * consistency guard, never an alternate identity.
 */

export type FeedbackType = "NPS" | "CSAT" | "CES" | "GENERAL_FEEDBACK" | "COMPLAINT";

export type FeedbackSource =
  | "WEBSITE"
  | "TYPEFORM"
  | "GOOGLE_FORMS"
  | "WHATSAPP"
  | "EMAIL"
  | "API"
  | "INTERNAL";

export interface FeedbackResponse {
  id: string;
  accountId: string;
  contactId: string | null;
  ticketId: string | null;
  type: FeedbackType;
  score: number | null;
  comment: string | null;
  source: FeedbackSource | null;
  externalReference: string | null;
  ownerUserId: string | null;
  createdAt: string;
}

export interface FeedbackCreateRequest {
  email: string;
  type: FeedbackType;
  score?: number;
  comment?: string;
  source?: FeedbackSource;
  externalReference?: string;
  expectedAccountId?: string;
  ticketId?: string;
}

export interface FeedbackListParams {
  accountId?: string;
  ticketId?: string;
  type?: FeedbackType;
  page?: number;
  size?: number;
}

export interface FeedbackListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export const FEEDBACK_TYPE_LABELS: Record<FeedbackType, string> = {
  NPS: "NPS",
  CSAT: "CSAT",
  CES: "CES",
  GENERAL_FEEDBACK: "General feedback",
  COMPLAINT: "Complaint",
};

export const FEEDBACK_SCORE_RANGES: Partial<
  Record<FeedbackType, { min: number; max: number; label: string }>
> = {
  NPS: { min: 0, max: 10, label: "0–10 (higher is better)" },
  CSAT: { min: 1, max: 5, label: "1–5 (higher is better)" },
  CES: { min: 1, max: 7, label: "1–7 (higher means more effort)" },
};

export function feedbackSourceLabel(source: FeedbackSource | null): string {
  if (!source) {
    return "Unknown";
  }
  const labels: Record<FeedbackSource, string> = {
    WEBSITE: "Website",
    TYPEFORM: "Typeform",
    GOOGLE_FORMS: "Google Forms",
    WHATSAPP: "WhatsApp",
    EMAIL: "Email",
    API: "API",
    INTERNAL: "Recorded by staff",
  };
  return labels[source];
}
