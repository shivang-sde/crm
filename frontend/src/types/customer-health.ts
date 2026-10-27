export type RiskSignalType =
  | "NEGATIVE_NPS"
  | "LOW_CSAT"
  | "HIGH_CES"
  | "RECENT_COMPLAINT"
  | "SLA_BREACHED_TICKET"
  | "ENTITLEMENT_APPROACHING_EXPIRY"
  | "ENTITLEMENT_EXPIRED"
  | "RECENT_DEAL_LOSS"
  | "ENGAGEMENT_FEEDBACK_SILENT"
  | "SUPPORT_SLOW_FIRST_RESPONSE"
  | "SUPPORT_PAST_RESOLUTION_DUE"
  | "SUPPORT_NO_RESPONSE_PAST_DUE";

export type RiskSignalSeverity = "HIGH" | "MEDIUM";

export interface RiskSignalResponse {
  accountId: string | null;
  type: RiskSignalType;
  severity: RiskSignalSeverity;
  reason: string;
  detectedAt: string;
  sourceType: string;
  sourceId: string;
  metadata?: Record<string, unknown> | null;
}

export interface RiskSignalAccountParams {
  from?: string;
  to?: string;
}

export interface RiskSignalListParams {
  from?: string;
  to?: string;
  type?: RiskSignalType;
  severity?: RiskSignalSeverity;
  page?: number;
  size?: number;
}

export interface RiskSignalListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export const RISK_SIGNAL_TYPE_LABELS: Record<RiskSignalType, string> = {
  NEGATIVE_NPS: "Negative NPS",
  LOW_CSAT: "Low CSAT",
  HIGH_CES: "High CES",
  RECENT_COMPLAINT: "Recent complaint",
  SLA_BREACHED_TICKET: "SLA breached ticket",
  ENTITLEMENT_APPROACHING_EXPIRY: "Entitlement approaching expiry",
  ENTITLEMENT_EXPIRED: "Entitlement expired",
  RECENT_DEAL_LOSS: "Recent deal loss",
  ENGAGEMENT_FEEDBACK_SILENT: "Feedback silent",
  SUPPORT_SLOW_FIRST_RESPONSE: "Slow first response",
  SUPPORT_PAST_RESOLUTION_DUE: "Past resolution due",
  SUPPORT_NO_RESPONSE_PAST_DUE: "No response past due",
};

export function riskSignalTypeLabel(type: string): string {
  return (
    RISK_SIGNAL_TYPE_LABELS[type as RiskSignalType] ??
    type
      .toLowerCase()
      .split("_")
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(" ")
  );
}

export function riskSignalSeverityLabel(severity: string): string {
  if (severity === "HIGH") return "High";
  if (severity === "MEDIUM") return "Medium";
  return "Unknown severity";
}

export function riskSignalSourceLabel(sourceType: string): string {
  const labels: Record<string, string> = {
    FEEDBACK: "Feedback",
    TICKET: "Ticket",
    ENTITLEMENT: "Entitlement",
    DEAL: "Deal",
  };
  return labels[sourceType.toUpperCase()] ?? riskSignalTypeLabel(sourceType);
}

export function riskSignalSourceHref(
  sourceType: string,
  sourceId: string,
): string | null {
  const routes: Record<string, string> = {
    TICKET: `/tickets/${sourceId}`,
    ENTITLEMENT: `/entitlements/${sourceId}`,
    DEAL: `/deals/${sourceId}`,
  };
  return routes[sourceType.toUpperCase()] ?? null;
}
