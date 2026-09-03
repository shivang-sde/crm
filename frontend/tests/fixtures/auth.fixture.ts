import { test as base, Page, BrowserContext } from '@playwright/test';
import { AuthResponse, User, Tenant } from '@/types/auth';
import { apiUrl } from '../helpers/api-url';

interface AuthFixture {
  apiLogin: (email: string, password: string) => Promise<AuthResponse>;
  uiLogin: (page: Page, email: string, password: string) => Promise<void>;
  clearAuth: (context: BrowserContext) => Promise<void>;
}

interface TestUser {
  email: string;
  password: string;
  user?: User;
  tenant?: Tenant;
  accessToken?: string;
}

export const test = base.extend<{
  auth: AuthFixture;
  testUser: TestUser;
}>({
  auth: async ({ request }, use) => {
    const apiLogin = async (email: string, password: string): Promise<AuthResponse> => {
      const response = await request.post(apiUrl('/auth/login'), {
        data: { email, password },
      });
      const body = await response.json();
      if (!response.ok() || !body.success || !body.data?.accessToken) {
        throw new Error(`Login failed: ${body.error?.message || response.statusText()}`);
      }
      return body.data;
    };

    const uiLogin = async (page: Page, email: string, password: string): Promise<void> => {
      await page.goto('/sign-in');
      await page.getByLabel('Email Address').fill(email);
      await page.getByLabel('Password').fill(password);
      await page.getByRole('button', { name: 'Sign In' }).click();
      await page.waitForURL('**/home', { timeout: 15000 });
    };

    const clearAuth = async (context: BrowserContext): Promise<void> => {
      await context.clearCookies();
      await context.addInitScript(() => {
        localStorage.removeItem('auth-storage');
      });
    };

    await use({ apiLogin, uiLogin, clearAuth });
  },

  testUser: async ({ auth }, use) => {
    const email = process.env.TEST_ADMIN_EMAIL || 'admin@e2e-test.local';
    const password = process.env.TEST_ADMIN_PASSWORD || 'TestPass123!';
    const authResponse = await auth.apiLogin(email, password);
    await use({
      email,
      password,
      user: authResponse.user,
      tenant: authResponse.tenant,
      accessToken: authResponse.accessToken,
    });
  },
});

export { expect } from '@playwright/test';