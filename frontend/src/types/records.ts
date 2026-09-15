export type RecordFieldType =
  | "TEXT"
  | "LONG_TEXT"
  | "INTEGER"
  | "DECIMAL"
  | "BOOLEAN"
  | "DATE"
  | "DATETIME"
  | "ENUM"
  | "URL"
  | "PHONE"
  | "EMAIL"
  | "JSON"
  | "REFERENCE";

export const RECORD_FIELD_TYPE_LABELS: Record<RecordFieldType, string> = {
  TEXT: "Text",
  LONG_TEXT: "Long Text",
  INTEGER: "Integer",
  DECIMAL: "Decimal",
  BOOLEAN: "Boolean",
  DATE: "Date",
  DATETIME: "Date & Time",
  ENUM: "Dropdown",
  URL: "URL",
  PHONE: "Phone",
  EMAIL: "Email",
  JSON: "JSON",
  REFERENCE: "Reference",
};

export interface RecordTypeResponse {
  id: string;
  tenantId: string;
  key: string;
  name: string;
  description?: string | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RecordTypeCreateRequest {
  key: string;
  name: string;
  description?: string;
  isActive?: boolean;
}

export interface RecordTypeUpdateRequest {
  name?: string;
  description?: string;
  isActive?: boolean;
}

export interface RecordFieldResponse {
  id: string;
  tenantId: string;
  recordTypeId: string;
  fieldKey: string;
  fieldLabel: string;
  fieldType: RecordFieldType;
  isRequired: boolean;
  isActive: boolean;
  displayOrder: number;
  options?: string[] | null;
  defaultValue?: string | null;
  referenceEntityType?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface RecordFieldCreateRequest {
  fieldKey: string;
  fieldLabel: string;
  fieldType: RecordFieldType;
  isRequired?: boolean;
  isActive?: boolean;
  displayOrder?: number;
  options?: string[];
  defaultValue?: string;
  referenceEntityType?: string;
}

export interface RecordFieldUpdateRequest {
  fieldLabel?: string;
  fieldType?: RecordFieldType;
  isRequired?: boolean;
  isActive?: boolean;
  displayOrder?: number;
  options?: string[];
  defaultValue?: string;
  referenceEntityType?: string;
}

export interface RecordTypeListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface DisplayListColumn {
  fieldId: string;
}

export interface DisplayListConfig {
  columns: DisplayListColumn[];
}

export interface DisplayDetailSection {
  id: string;
  name: string;
  fieldIds: string[];
}

export interface DisplayDetailConfig {
  sections: DisplayDetailSection[];
}

export interface DisplayConfig {
  list: DisplayListConfig;
  detail: DisplayDetailConfig;
}

export interface DisplayConfigResponse {
  recordTypeId: string;
  tenantId: string;
  list: DisplayListConfig;
  detail: DisplayDetailConfig;
  isCustom: boolean;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface DisplayConfigRequest {
  list: DisplayListConfig;
  detail: DisplayDetailConfig;
}

export interface CrmRecordResponse {
  id: string;
  tenantId: string;
  recordTypeId: string;
  recordTypeKey: string;
  recordTypeName: string;
  data: Record<string, unknown>;
  ownerId?: string | null;
  createdBy?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CrmRecordCreateRequest {
  recordTypeId: string;
  data: Record<string, unknown>;
}

export interface CrmRecordUpdateRequest {
  data: Record<string, unknown>;
}

export interface CrmRecordListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface CrmRecordListParams {
  recordTypeId?: string;
  page?: number;
  size?: number;
  search?: string;
  sort?: string;
  direction?: string;
}

export type MappingProfileMode = "DIRECT" | "CUSTOM";

export type MappingTransform = "IDENTITY" | "STRING" | "INTEGER" | "DECIMAL" | "BOOLEAN" | "DATE" | "DATETIME" | "ENUM";

export interface MappingEntry {
  source: string;
  targetFieldId: string;
  transform?: MappingTransform;
}

export interface RecordMappingProfileResponse {
  id: string;
  tenantId: string;
  recordTypeId: string;
  recordTypeKey?: string;
  recordTypeName?: string;
  mappingKey: string;
  name: string;
  description?: string | null;
  mode: MappingProfileMode;
  configuration: { mappings?: MappingEntry[] };
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RecordMappingProfileCreateRequest {
  recordTypeId: string;
  mappingKey: string;
  name: string;
  description?: string;
  mode: MappingProfileMode;
  configuration?: { mappings?: MappingEntry[] };
  isActive?: boolean;
}

export interface RecordMappingProfileUpdateRequest {
  name?: string;
  description?: string;
  mode?: MappingProfileMode;
  configuration?: { mappings?: MappingEntry[] };
  isActive?: boolean;
}

export interface MappingProfilesListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export type WebhookAuthMode = "NONE" | "API_KEY" | "HMAC_SHA256";

export interface RecordWebhookResponse {
  id: string;
  tenantId: string;
  name: string;
  webhookKey: string;
  description?: string | null;
  recordTypeId: string;
  recordTypeKey?: string;
  recordTypeName?: string;
  mappingProfileId?: string | null;
  mappingKey?: string | null;
  mappingName?: string | null;
  mappingMode?: MappingProfileMode | null;
  isActive: boolean;
  authMode: WebhookAuthMode;
  endpointPath: string;
  createdAt: string;
  updatedAt: string;
}

export interface RecordWebhookCreateRequest {
  name: string;
  webhookKey: string;
  description?: string;
  recordTypeId: string;
  mappingProfileId?: string | null;
  isActive?: boolean;
  authMode?: WebhookAuthMode;
}

export interface RecordWebhookUpdateRequest {
  name?: string;
  description?: string;
  recordTypeId?: string;
  mappingProfileId?: string | null;
  isActive?: boolean;
  authMode?: WebhookAuthMode;
}

export interface RotateWebhookSecretResponse {
  secret: string;
  webhook: RecordWebhookResponse;
}

export interface RecordWebhooksListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface RecordWebhookDeliveryResponse {
  id: string;
  tenantId: string;
  webhookId: string;
  webhookKey: string;
  webhookName?: string | null;
  recordTypeId?: string | null;
  recordTypeKey?: string | null;
  recordTypeName?: string | null;
  mappingProfileId?: string | null;
  mappingProfileKey?: string | null;
  mappingProfileName?: string | null;
  mappingMode?: string | null;
  status: string;
  failureStage?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  payloadHash: string;
  idempotencyKey: string;
  recordId?: string | null;
  eventId?: string | null;
  responseStatus?: number | null;
  receivedAt: string;
  createdAt: string;
  updatedAt: string;
  idempotencyApplied?: boolean;
}

export interface RecordWebhookDeliveryDetailResponse {
  delivery: {
    id: string;
    status: string;
    failureStage?: string | null;
    responseStatus?: number | null;
    errorCode?: string | null;
    errorMessage?: string | null;
    payloadHash: string;
    idempotencyKey: string;
    receivedAt: string;
    createdAt: string;
  };
  webhook: { id: string; key: string; name: string } | null;
  recordType: { id: string; key: string; name: string } | null;
  mappingProfile: { id: string; key: string; name: string; mode?: string | null } | null;
  idempotency: { applied: boolean; key: string; payloadHash: string };
  record: { id: string } | null;
  event: { eventId: string; entityType: string; eventType: string } | null;
  workflow: { triggered: boolean; executionCount: number; executions: Array<{ id: string; workflowId: string; workflowName?: string | null; workflowVersionId: string; status: string; triggerEventId: string }> };
  failure: { stage?: string | null; code?: string | null; message?: string | null };
}

export interface RecordWebhookDeliveriesListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}
