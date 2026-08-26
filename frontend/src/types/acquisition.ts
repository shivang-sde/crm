import z from "zod";

export type LeadIngestionTransportType =
  | "WEBHOOK"
  | "API"
  | "FORM"
  | "CONNECTOR"
  | "POLLING"
  | "IMPORT";

export const leadIngestionTransportTypes: LeadIngestionTransportType[] = [
  "WEBHOOK",
  "API",
  "FORM",
  "CONNECTOR",
  "POLLING",
  "IMPORT",
];

export interface LeadIngestionConfigResponse {
  id: string;
  name: string;
  transportType: LeadIngestionTransportType;
  publicKey: string | null;
  inboundPath?: string | null;
  active?: boolean | null;
  settings?: Record<string, unknown> | null;
  createdAt: string;
  updatedAt: string;
}

export interface LeadIngestionConfigCreateRequest {
  name: string;
  transportType: LeadIngestionTransportType;
  active?: boolean | null;
  settings?: Record<string, unknown> | null;
}

export type LeadIngestionConfigUpdateRequest = LeadIngestionConfigCreateRequest;

export const acquisitionConfigSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, { message: "Configuration name is required" })
    .max(200, { message: "Configuration name cannot exceed 200 characters" }),
  transportType: z.enum([
    "WEBHOOK",
    "API",
    "FORM",
    "CONNECTOR",
    "POLLING",
    "IMPORT",
  ]),
  active: z.boolean(),
});

export type AcquisitionConfigFormInput = z.input<typeof acquisitionConfigSchema>;
export type AcquisitionConfigFormOutput = z.output<typeof acquisitionConfigSchema>;

export type LeadIngestionTargetType =
  | "STANDARD_FIELD"
  | "SYSTEM_FIELD"
  | "CUSTOM_FIELD";

export type LeadIngestionTransformType =
  | "NONE"
  | "TRIM"
  | "LOWERCASE"
  | "UPPERCASE";

export interface LeadIngestionSourceField {
  path: string;
  sampleValue?: unknown;
  detectedType?: string | null;
}

export interface LeadIngestionTargetField {
  targetType: LeadIngestionTargetType;
  fieldKey: string;
  label: string;
  dataType?: string | null;
  required?: boolean | null;
}

export interface LeadIngestionFieldMappingResponse {
  id: string;
  sourcePath: string;
  targetType: LeadIngestionTargetType;
  targetField: string;
  transformType: LeadIngestionTransformType;
  transformConfig?: Record<string, unknown> | null;
  defaultValue?: string | null;
  required?: boolean | null;
  active?: boolean | null;
  displayOrder?: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface LeadIngestionFieldMappingRequest {
  sourcePath: string;
  targetType: LeadIngestionTargetType;
  targetField: string;
  transformType?: LeadIngestionTransformType | null;
  transformConfig?: Record<string, unknown> | null;
  defaultValue?: string | null;
  required?: boolean | null;
  active?: boolean | null;
  displayOrder?: number | null;
}

export interface LeadIngestionValidationError {
  field?: string | null;
  code?: string | null;
  message?: string | null;
}

export interface MappedLeadData {
  standardFields?: Record<string, unknown> | null;
  systemFields?: Record<string, unknown> | null;
  customFields?: Record<string, unknown> | null;
  errors?: string[] | null;
}

export interface ValidatedLeadIngestionData {
  standardFields?: Record<string, unknown> | null;
  systemFields?: Record<string, unknown> | null;
  customFields?: Record<string, unknown> | null;
  errors?: LeadIngestionValidationError[] | null;
}

export const acquisitionMappingSchema = z.object({
  sourcePath: z
    .string()
    .trim()
    .min(1, { message: "Source field is required" })
    .max(500, { message: "Source path cannot exceed 500 characters" }),
  targetType: z.enum(["STANDARD_FIELD", "SYSTEM_FIELD", "CUSTOM_FIELD"]),
  targetField: z
    .string()
    .trim()
    .min(1, { message: "Target field is required" })
    .max(100, { message: "Target field cannot exceed 100 characters" }),
  transformType: z.enum(["NONE", "TRIM", "LOWERCASE", "UPPERCASE"]),
  defaultValue: z.string().max(255, { message: "Default value is too long" }).optional(),
  required: z.boolean(),
  active: z.boolean(),
  displayOrder: z.number().int().min(0).max(9999),
});

export type AcquisitionMappingFormInput = z.input<typeof acquisitionMappingSchema>;
export type AcquisitionMappingFormOutput = z.output<typeof acquisitionMappingSchema>;

export type LeadIngestionEventStatus =
  | "RECEIVED"
  | "PROCESSING"
  | "PROCESSED"
  | "REJECTED"
  | "FAILED";

export const leadIngestionEventStatuses: LeadIngestionEventStatus[] = [
  "RECEIVED",
  "PROCESSING",
  "PROCESSED",
  "REJECTED",
  "FAILED",
];

export interface LeadIngestionEventSummaryResponse {
  id: string;
  ingestionConfigId: string;
  status: LeadIngestionEventStatus;
  externalEventId: string | null;
  leadId: string | null;
  errorCode: string | null;
  receivedAt: string;
  processedAt: string | null;
}

export interface LeadIngestionEventDetailResponse {
  id: string;
  ingestionConfigId: string;
  externalEventId: string | null;
  idempotencyKey: string | null;
  status: LeadIngestionEventStatus;
  leadId: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  receivedAt: string;
  processedAt: string | null;
  rawPayload?: Record<string, unknown> | null;
  headers?: Record<string, unknown> | null;
  createdAt: string;
  updatedAt: string;
}

export interface LeadIngestionEventListParams {
  status?: LeadIngestionEventStatus;
  page?: number;
  size?: number;
}

export interface LeadIngestionEventListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}
