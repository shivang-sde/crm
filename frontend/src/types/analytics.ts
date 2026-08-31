export type AnalyticsScope = "PLATFORM" | "RESELLER" | "TENANT" | "TEAM" | "USER";

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

export type GroupedDataset =
  | "pipeline-stage"
  | "pipeline-owner"
  | "pipeline-account"
  | "conversion-owner"
  | "deals-aging"
  | "calls-status";

export interface PipelineStageRow {
  stageId: string;
  stageName: string;
  openCount: number;
  wonCount: number;
  lostCount: number;
  pipelineValue: number;
  wonValue: number;
  totalCount: number;
}

export interface PipelineOwnerRow {
  ownerUserId: string | null;
  ownerDisplayName: string | null;
  openCount: number;
  wonCount: number;
  lostCount: number;
  pipelineValue: number;
  wonValue: number;
  totalCount: number;
}

export interface PipelineAccountRow {
  accountId: string;
  accountName: string | null;
  openCount: number;
  wonCount: number;
  lostCount: number;
  pipelineValue: number;
  wonValue: number;
  totalCount: number;
}

export interface ConversionOwnerRow {
  ownerUserId: string | null;
  ownerDisplayName: string | null;
  newLeadCount: number;
  convertedLeadCount: number;
  conversionRate: number;
}

export interface DealAgingRow {
  bucket: string;
  count: number;
  pipelineValue: number;
}

export interface CallStatusSummary {
  planned: number;
  held: number;
  notHeld: number;
  cancelled: number;
  heldRate: number;
}
