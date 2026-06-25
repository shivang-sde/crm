export type DealFieldType =
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

export interface DealCustomFieldResponse {
  id: string;
  fieldKey: string;
  fieldLabel: string;
  fieldType: DealFieldType;
  isRequired: boolean;
  isActive: boolean;
  displayOrder: number;
  options?: Array<{ label: string; value: string }>;
  createdAt?: string;
}

export interface DealCustomFieldCreateRequest {
  fieldKey: string;
  fieldLabel: string;
  fieldType: DealFieldType;
  isRequired?: boolean;
  isActive?: boolean;
  displayOrder?: number;
  options?: Array<{ label: string; value: string }>;
}
