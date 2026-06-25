export type LeadFieldType =
  | "TEXT"
  | "TEXTAREA"
  | "NUMBER"
  | "EMAIL"
  | "PHONE"
  | "DATE"
  | "BOOLEAN"
  | "SELECT"
  | "MULTISELECT"
  | "URL";

export interface LeadStatusSummary {
  id: string;
  name: string;
  color?: string;
  displayOrder?: number;
  isDefault?: boolean;
  isClosed?: boolean;
  createdAt?: string;
}

export interface LeadSourceSummary {
  id: string;
  name: string;
  isActive?: boolean;
  createdAt?: string;
}

export interface LeadResponse {
  id: string;
  firstName: string;
  lastName?: string;
  email?: string;
  phone?: string;
  company?: string;
  status: LeadStatusSummary;
  source?: LeadSourceSummary;
  ownerUserId?: string;
  score: number;
  notes?: string;
  isConverted: boolean;
  convertedAt?: string;
  customData?: Record<string, unknown>;
  createdBy?: string;
  createdAt: string;
  updatedAt: string;
}

export interface LeadCreateRequest {
  firstName: string;
  lastName?: string;
  email?: string;
  phone?: string;
  company?: string;
  statusId: string;
  sourceId?: string;
  ownerUserId?: string;
  score?: number;
  notes?: string;
  customData?: Record<string, unknown>;
}

export interface LeadUpdateRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  phone?: string;
  company?: string;
  statusId?: string;
  sourceId?: string;
  ownerUserId?: string;
  score?: number;
  notes?: string;
  customData?: Record<string, unknown>;
}

export interface LeadConvertRequest {
  accountId?: string;
  contactId?: string;
}

export interface LeadConvertResponse {
  leadId: string;
  accountId: string;
  contactId: string;
}

export interface LeadCustomFieldResponse {
  id: string;
  fieldKey: string;
  fieldLabel: string;
  fieldType: LeadFieldType;
  isRequired: boolean;
  isActive: boolean;
  displayOrder: number;
  options?: Array<{ label: string; value: string }>;
  createdAt?: string;
}

export interface LeadActivityResponse {
  id: string;
  leadId: string;
  activityType: string;
  description: string;
  performedBy?: string;
  metadata?: Record<string, unknown>;
  createdAt: string;
}

export interface LeadNoteResponse {
  id: string;
  leadId: string;
  note: string;
  createdBy?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface LeadListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface LeadListParams {
  page?: number;
  size?: number;
  search?: string;
  status?: string;
  source?: string;
  owner?: string;
  converted?: boolean;
}

export interface LeadStatusCreateRequest {
  name: string;
  color?: string;
  displayOrder?: number;
  isDefault?: boolean;
  isClosed?: boolean;
}

export interface LeadSourceCreateRequest {
  name: string;
  isActive?: boolean;
}

export interface LeadCustomFieldCreateRequest {
  fieldKey: string;
  fieldLabel: string;
  fieldType: LeadFieldType;
  isRequired?: boolean;
  isActive?: boolean;
  displayOrder?: number;
  options?: Array<{ label: string; value: string }>;
}
