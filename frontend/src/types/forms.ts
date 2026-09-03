export type FormStatus = "DRAFT" | "PUBLISHED" | "UNPUBLISHED";

export type FormFieldType =
  | "TEXT"
  | "TEXTAREA"
  | "EMAIL"
  | "PHONE"
  | "NUMBER"
  | "DATE"
  | "URL"
  | "SELECT"
  | "MULTISELECT"
  | "RADIO"
  | "CHECKBOX"
  | "HIDDEN";

export interface FormFieldOption {
  label: string;
  value: string;
}

export interface FormField {
  id: string;
  fieldKey: string;
  type: FormFieldType;
  label: string;
  placeholder?: string | null;
  helpText?: string | null;
  required?: boolean | null;
  orderIndex: number;
  defaultValue?: string | null;
  options?: FormFieldOption[] | null;
  crmTargetType?: string | null;
  crmTargetField?: string | null;
  transformType?: string | null;
  transformConfig?: Record<string, unknown> | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface FormResponse {
  id: string;
  tenantId: string;
  name: string;
  description?: string | null;
  status: FormStatus;
  publicKey?: string | null;
  acquisitionConfigId?: string | null;
  settings?: Record<string, unknown> | null;
  publishedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  fields: FormField[];
  publicUrl?: string | null;
  submissionCount?: number | null;
}

export interface FormCreateRequest {
  name: string;
  description?: string | null;
}

export interface FormUpdateRequest {
  name?: string | null;
  description?: string | null;
  settings?: Record<string, unknown> | null;
  fields?: FormFieldRequest[] | null;
}

export interface FormFieldRequest {
  id?: string | null;
  fieldKey: string;
  type: string;
  label: string;
  placeholder?: string | null;
  helpText?: string | null;
  required?: boolean | null;
  orderIndex?: number | null;
  defaultValue?: string | null;
  options?: FormFieldOption[] | null;
  crmTargetType?: string | null;
  crmTargetField?: string | null;
  transformType?: string | null;
  transformConfig?: Record<string, unknown> | null;
}
