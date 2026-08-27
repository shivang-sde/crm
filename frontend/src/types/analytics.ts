export type AnalyticsScope = "PLATFORM" | "RESELLER" | "TENANT" | "USER";

export interface LeadMetrics {
  newLeads: number;
  convertedLeads: number;
  conversionRate: number;
}

export interface DealMetrics {
  openDeals: number;
  wonDeals: number;
  lostDeals: number;
  pipelineValue: number;
  wonValue: number;
  winRate: number;
}

export interface ActivityMetrics {
  openTasks: number;
  completedTasks: number;
  overdueTasks: number;
}

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
  leadMetrics: LeadMetrics;
  dealMetrics: DealMetrics;
  activityMetrics: ActivityMetrics;
}

export interface AnalyticsTrendPoint {
  bucket: string;
  leads: number;
  contacts: number;
  deals: number;
  tasks: number;
}

export interface AnalyticsDateRange {
  from: string | null;
  to: string | null;
}
