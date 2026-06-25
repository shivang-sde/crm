export type RepeatType = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY' | 'CUSTOM';
export type CustomFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';
export type EndType = 'NEVER' | 'AFTER_N_TIMES' | 'ON_DATE';

export interface Recurrence {
  repeatType: RepeatType;
  customFrequency?: CustomFrequency;
  interval?: number;
  endType: EndType;
  endAfterCount?: number;
  endDate?: string;
}
