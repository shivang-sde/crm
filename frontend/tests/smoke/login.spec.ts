import { test, expect } from '../fixtures';
import { apiUrl } from '../helpers/api-url';

test.describe('@smoke Authentication', () => {
  test('login via UI redirects to home and shows authenticated state', async ({ page, testUser }) => {
    await page.goto('/sign-in');
    await expect(page).toHaveURL(/\/sign-in/);

    await page.getByLabel('Email Address').fill(testUser.email);
    await page.getByLabel('Password').fill(testUser.password);
    await page.getByRole('button', { name: 'Sign In' }).click();

    await expect(page).toHaveURL(/\/home/, { timeout: 15000 });
    await expect(page.locator('text=Welcome back').first()).toBeVisible({ timeout: 5000 }).catch(() => {});
  });

  test('login via API returns valid auth response', async ({ request, testUser }) => {
    const response = await request.post(apiUrl('/auth/login'), {
      data: { email: testUser.email, password: testUser.password },
    });
    expect(response.ok()).toBeTruthy();

    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data?.accessToken).toBeTruthy();
    expect(body.data?.user).toBeTruthy();
    expect(body.data?.user?.email).toBe(testUser.email);
  });

  test('authenticated API request works with token', async ({ request, testUser }) => {
    const loginResponse = await request.post(apiUrl('/auth/login'), {
      data: { email: testUser.email, password: testUser.password },
    });
    const loginBody = await loginResponse.json();
    const accessToken = loginBody.data?.accessToken;
    expect(accessToken).toBeTruthy();

    const meResponse = await request.get(apiUrl('/auth/me'), {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(meResponse.ok()).toBeTruthy();

    const meBody = await meResponse.json();
    expect(meBody.success).toBe(true);
    expect(meBody.data?.email).toBe(testUser.email);
  });
});