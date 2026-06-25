export interface AccountListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface AccountListParams {
  search?: string;
  owner?: string;
  page?: number;
  size?: number;
}

export interface AccountCreateRequest {
  name: string;
  website?: string;
  industry?: string;
  phone?: string;
  email?: string;
  annualRevenue?: number;
  employeeCount?: number;
  description?: string;
  country?: string;
  state?: string;
  city?: string;
  addressLine1?: string;
  postalCode?: string;
  ownerUserId?: string;
  customData?: Record<string, unknown>;
}

export interface AccountUpdateRequest {
  name?: string;
  website?: string;
  industry?: string;
  phone?: string;
  email?: string;
  annualRevenue?: number;
  employeeCount?: number;
  description?: string;
  country?: string;
  state?: string;
  city?: string;
  addressLine1?: string;
  postalCode?: string;
  ownerUserId?: string;
  customData?: Record<string, unknown>;
}

export interface AccountResponse {
  id: string;
  name: string;
  website?: string;
  industry?: string;
  phone?: string;
  email?: string;
  annualRevenue?: number;
  employeeCount?: number;
  description?: string;
  country?: string;
  state?: string;
  city?: string;
  addressLine1?: string;
  postalCode?: string;
  ownerUserId?: string;
  leadId?: string;
  customData?: Record<string, unknown>;
  isActive?: boolean;
  createdAt: string;
  updatedAt: string;
}
