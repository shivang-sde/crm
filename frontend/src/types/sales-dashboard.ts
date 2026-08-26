export interface DealDashboardSummary {
  totalCount: number;
  openCount: number;
  wonCount: number;
  lostCount: number;
  open_pipeline_value: number;
  weighted_pipeline_value: number;
  won_value: number;
  lost_value: number;
  average_open_deal_size: number;
  average_days_in_pipeline?: number | null;
  max_open_deal_age_days?: number | null;
  average_days_in_current_stage?: number | null;
  stale_deal_count: number;
  stale_deal_value: number;
  stale_deal_weighted_value: number;
  stale_deal_percentage?: number | null;
  forecast_by_category: Record<string, number>;
}

export interface StageBreakdown {
  stageId: string;
  stageName: string;
  color?: string | null;
  displayOrder?: number | null;
  recordCategory?: string | null;
  count: number;
  total_amount: number;
  average_days_in_stage?: number | null;
  stale_count: number;
}

export interface OwnerBreakdown {
  owner_user_id?: string | null;
  owner_name: string;
  open_count: number;
  won_count: number;
  lost_count: number;
  open_value: number;
  won_value: number;
}

export interface LeadFunnel {
  total_leads: number;
  open_leads: number;
  converted_leads: number;
  conversion_rate_percent: number;
}

export interface ClosingMetrics {
  expected_close_next_30_days_count: number;
  expected_close_next_30_days_value: number;
  overdue_expected_close_count: number;
  overdue_expected_close_value: number;
  average_sales_cycle_days?: number | null;
  won_value_last_30_days: number;
}

export interface SalesDashboardResponse {
  deals: DealDashboardSummary;
  stage_breakdown: StageBreakdown[];
  owner_breakdown: OwnerBreakdown[];
  lead_funnel: LeadFunnel;
  closing: ClosingMetrics;
}
