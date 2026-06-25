export interface Tenant {
  id: string;

  name: string;
  slug: string;

  companyEmail?: string;
  companyPhone?: string;
  website?: string;

  country?: string;
  state?: string;
  city?: string;
  addressLine1?: string;
  postalCode?: string;

  logoUrl?: string;
  primaryColor?: string;

  industry?: string;
  timezone?: string;
  currencyCode?: string;
  language?: string;

  planType?: string;

  currentUsers?: number;
  maxUsers?: number;
  
  subscriptionEndDate?: string;

  isActive: boolean;

  resellerId?: string;

  createdAt?: string;
  updatedAt?: string;
  
}

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  tenantId: string;
  role?: string;
  roleId?: string;
  roleName?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AuthResponse {
  accessToken: string;
  user: User;
  tenant?: Tenant;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  meta?: unknown;
  error?: {
    code?: string;
    message?: string;
  };
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  companyName: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}
