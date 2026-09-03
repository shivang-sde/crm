import { test as base } from '@playwright/test';
import { TenantProvisionRequest, TenantResponse, TenantProvisionResponse } from '@/types/tenant';
import { User } from '@/types/auth';
import { apiUrl } from '../helpers/api-url';

interface TenantFixture {
  provisionTenant: (data: TenantProvisionRequest) => Promise<TenantProvisionResponse>;
  getTenant: (tenantId: string) => Promise<TenantResponse>;
  deleteTenant: (tenantId: string) => Promise<void>;
  createTestTenant: (suffix?: string) => Promise<TenantProvisionResponse>;
  cleanupTenant: (tenantId: string) => Promise<void>;
}

export const test = base.extend<{
  tenantApi: TenantFixture;
}>({
  tenantApi: async ({ request }, use) => {
    const provisionTenant = async (data: TenantProvisionRequest): Promise<TenantProvisionResponse> => {
      const response = await request.post(apiUrl('/tenants/provision'), { data });
      const body = await response.json();
      if (!response.ok() || !body.success) {
        throw new Error(`Tenant provision failed: ${body.error?.message || response.statusText()}`);
      }
      return body.data;
    };

    const getTenant = async (tenantId: string): Promise<TenantResponse> => {
      const response = await request.get(apiUrl(`/tenants/${tenantId}`));
      const body = await response.json();
      if (!response.ok() || !body.success) {
        throw new Error(`Get tenant failed: ${body.error?.message || response.statusText()}`);
      }
      return body.data;
    };

    const deleteTenant = async (tenantId: string): Promise<void> => {
      const response = await request.delete(apiUrl(`/tenants/${tenantId}`));
      if (!response.ok()) {
        const body = await response.json();
        throw new Error(`Delete tenant failed: ${body.error?.message || response.statusText()}`);
      }
    };

    const createdTenants: string[] = [];

    const createTestTenant = async (suffix = Date.now().toString()): Promise<TenantProvisionResponse> => {
      const tenantName = `E2E Test Tenant ${suffix}`;
      const adminEmail = `admin-${suffix}@e2e-test.local`;
      
      const result = await provisionTenant({
        companyName: tenantName,
        admin: {
          email: adminEmail,
          password: 'TestPass123!',
          firstName: 'Test',
          lastName: 'Admin',
        },
      });

      createdTenants.push(result.tenant.id);
      return result;
    };

    const cleanupTenant = async (tenantId: string): Promise<void> => {
      try {
        await deleteTenant(tenantId);
        const idx = createdTenants.indexOf(tenantId);
        if (idx > -1) createdTenants.splice(idx, 1);
      } catch (error) {
        console.warn(`Failed to cleanup tenant ${tenantId}:`, error);
      }
    };

    await use({ provisionTenant, getTenant, deleteTenant, createTestTenant, cleanupTenant });

    for (const tenantId of createdTenants) {
      try {
        await deleteTenant(tenantId);
      } catch {
        // best effort cleanup
      }
    }
  },
});

export { expect } from '@playwright/test';