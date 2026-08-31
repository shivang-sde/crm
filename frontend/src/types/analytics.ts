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
  | "calls-status"
  | "conversion-period"
  | "forecast-category"
  | "current-stage-age"
  | "activity-rates"
  | "calls-duration"
  | "lead-source"
  | "contacts-account"
  | "accounts-owner";

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

export interface ConversionPeriodSummary {
  convertedDuringPeriod: number;
}

export interface ForecastCategoryRow {
  category: string;
  dealCount: number;
  pipelineValue: number;
  wonValue: number;
}

export interface CurrentStageAgeSummary {
  avgDealAgeDays: number;
  avgCurrentStageAgeDays: number;
  openDealsWithStageEnteredAt: number;
  openDealsWithoutStageEnteredAt: number;
}

export interface MeetingStatusSummary {
  planned: number;
  held: number;
  notHeld: number;
  cancelled: number;
  heldRate: number;
}

export interface ActivityRatesSummary {
  taskCompletionRate: number;
  taskOverdueRate: number;
  meetingStatus: MeetingStatusSummary;
}

export interface CallDurationSummary {
  callsTotal: number;
  callsWithDuration: number;
  callsWithoutDuration: number;
  totalCallMinutes: number;
  averageCallDurationMinutes: number;
}

export interface LeadSourcePerformanceRow {
  sourceId: string | null;
  source: string;
  leadCount: number;
  convertedCount: number;
  conversionRate: number;
}

export interface ContactsPerAccountRow {
  accountId: string | null;
  accountName: string;
  contactCount: number;
}

export interface AccountsByOwnerRow {
  ownerUserId: string | null;
  ownerDisplayName: string | null;
  accountCount: number;
  activeCount: number;
}
