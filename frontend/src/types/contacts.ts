export interface ContactListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface ContactListParams {
  search?: string;
  accountId?: string;
  owner?: string;
  page?: number;
  size?: number;
}

export interface ContactCreateRequest {
  accountId: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  phone?: string;
  title?: string;
  department?: string;
  ownerUserId?: string;
  customData?: Record<string, unknown>;
}

export interface ContactUpdateRequest {
  accountId?: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  phone?: string;
  title?: string;
  department?: string;
  ownerUserId?: string;
  customData?: Record<string, unknown>;
}

export interface ContactResponse {
  id: string;
  accountId: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  phone?: string;
  title?: string;
  department?: string;
  ownerUserId?: string;
  customData?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}
