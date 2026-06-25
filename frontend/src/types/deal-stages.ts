export type RecordCategory = "OPEN" | "CLOSED_WON" | "CLOSED_LOST";
export type ForecastCategory = "PIPELINE" | "BEST_CASE" | "COMMIT" | "CLOSED" | "OMITTED";

export interface DealStageSummary {
  id: string;
  name: string;
  color?: string;
  displayOrder?: number;
  isDefault?: boolean;
  isClosed?: boolean;
  recordCategory?: RecordCategory;
  defaultProbability?: number;
  defaultForecastCategory?: ForecastCategory;
  createdAt?: string;
}

export interface DealStageCreateRequest {
  name: string;
  color?: string;
  displayOrder?: number;
  isDefault?: boolean;
  isClosed?: boolean;
  recordCategory?: RecordCategory;
  defaultProbability?: number;
  defaultForecastCategory?: ForecastCategory;
}
