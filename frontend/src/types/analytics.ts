export type AnalyticsScope = "PLATFORM" | "RESELLER" | "TENANT" | "USER";

export interface AnalyticsSummaryResponse {
  scope: AnalyticsScope;
  from: string;
  to: string;
  leads: number;
  contacts: number;
  deals: number;
  tasks: number;
  calls: number;
  meetings: number;
}

export interface AnalyticsDateRange {
  from: string | null;
  to: string | null;
}
