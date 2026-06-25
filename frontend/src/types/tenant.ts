import { User } from "@/types/rbac";
import { Tenant } from "@/types/auth";

export interface TenantProvisionRequest {
  companyName: string;

  companyEmail?: string;
  companyPhone?: string;
  website?: string;
  subscriptionEndDate?: string;

  maxUsers?: number;

  resellerId?: string | null;

  admin: {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
  };
}

export interface TenantProvisionResponse {
  tenant: Tenant;
  admin: User;
}

export interface TenantResponse {
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

  planType: string;
  maxUsers?: number;
  currentUsers?: number;
  subscriptionEndDate?: string;
  
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  reseller?: {
    id: string;
    email: string;
    firstName: string;
    lastName: string;
  };
}


export interface TenantUpdateRequest {
  companyName: string;
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

  maxUsers?: number;

  // LocalDate in Java → string in TS ("YYYY-MM-DD")
  subscriptionEndDate?: string;

  isActive?: boolean;
}

function mapProvisionResponseToTenant(res: TenantProvisionResponse): TenantResponse {
  const { tenant, admin } : {tenant:Tenant, admin:User} = res;

  return {
    id: tenant.id,
    name: tenant.name,
    slug: tenant.slug,

    companyEmail: tenant.companyEmail,
    companyPhone: tenant.companyPhone,
    website: tenant.website,

    country: tenant.country,
    state: tenant.state,
    city: tenant.city,
    addressLine1: tenant.addressLine1,
    postalCode: tenant.postalCode,

    logoUrl: tenant.logoUrl,
    primaryColor: tenant.primaryColor,

    industry: tenant.industry,
    timezone: tenant.timezone,
    currencyCode: tenant.currencyCode,
    language: tenant.language,

    planType: tenant.planType as string,
    maxUsers: tenant.maxUsers as number,
    currentUsers: tenant.currentUsers as number,
    subscriptionEndDate: tenant.subscriptionEndDate as string,

    isActive: tenant.isActive,
    createdAt: tenant.createdAt as string,
    updatedAt: tenant.updatedAt as string,

    

    // optional: if you want admin info, you can extend TenantResponse
    // or attach it separately in your UI
  };
}



