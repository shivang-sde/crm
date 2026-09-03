import { test, expect } from '../fixtures';
import { createApiHelper } from '../helpers/api-helper';

test.describe('@smoke Lead Creation', () => {
  test('create lead via UI and verify via API', async ({ page, testUser, request }) => {
    const api = createApiHelper(request, testUser.accessToken);

    await page.goto('/sign-in');
    await page.getByLabel('Email Address').fill(testUser.email);
    await page.getByLabel('Password').fill(testUser.password);
    await page.getByRole('button', { name: 'Sign In' }).click();
    await expect(page).toHaveURL(/\/home/, { timeout: 15000 });

    await page.goto('/leads/new');
    await expect(page).toHaveURL(/\/leads\/new/);
    await expect(page.getByRole('button', { name: 'Create Lead' })).toBeVisible({ timeout: 15000 });
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(()=>{});

    const testLead = {
      firstName: 'E2E',
      lastName: `TestLead-${Date.now()}`,
      email: `e2e-lead-${Date.now()}@example.com`,
      phone: '+15551234567',
      company: 'E2E Test Company',
    };

    await page.locator('input[name="firstName"]').fill(testLead.firstName);
    await page.locator('input[name="lastName"]').fill(testLead.lastName);
    await page.locator('input[name="email"]').fill(testLead.email);
    await page.locator('input[name="phone"]').fill(testLead.phone);
    await page.locator('input[name="company"]').fill(testLead.company);

    // Status is pre-filled with default; ensure combobox is ready
    await expect(page.getByRole('combobox').first()).toBeVisible({ timeout: 5000 }).catch(()=>{});

    await page.getByRole('button', { name: 'Create Lead' }).click();

    await expect(page).toHaveURL(/\/leads\/[a-f0-9-]+/, { timeout: 15000 });
    const leadId = page.url().split('/leads/')[1];

    await expect(page.getByText(testLead.firstName)).toBeVisible({ timeout: 5000 });
    await expect(page.getByText(testLead.lastName)).toBeVisible({ timeout: 5000 });
    await expect(page.getByText(testLead.email)).toBeVisible({ timeout: 5000 });

    const apiLead = await api.getLead(leadId);
    expect(apiLead.id).toBe(leadId);
    expect(apiLead.firstName).toBe(testLead.firstName);
    expect(apiLead.lastName).toBe(testLead.lastName);
    expect(apiLead.email).toBe(testLead.email);
    expect(apiLead.company).toBe(testLead.company);
  });

  test('create lead via API directly', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    let statuses = await api.listLeadStatuses();
    let statusId = statuses.find((s: any) => s.isDefault)?.id || statuses[0]?.id;
    if (!statusId) {
      const created = await api.createLeadStatus({ name: 'New', color: '#3b82f6', isDefault: true } as any);
      statusId = (created as any).id || (created as any).data?.id || statuses[0]?.id;
      if (!statusId) { statuses = await api.listLeadStatuses(); statusId = statuses[0]?.id; }
    }
    const testLead = {
      firstName: 'API',
      lastName: `DirectLead-${Date.now()}`,
      email: `api-direct-${Date.now()}@example.com`,
      company: 'API Test Company',
      statusId,
    };

    const createdLead = await api.createLead(testLead);
    expect(createdLead.id).toBeTruthy();
    expect(createdLead.firstName).toBe(testLead.firstName);
    expect(createdLead.lastName).toBe(testLead.lastName);
    expect(createdLead.email).toBe(testLead.email);

    const fetchedLead = await api.getLead(createdLead.id);
    expect(fetchedLead.id).toBe(createdLead.id);
    expect(fetchedLead.firstName).toBe(testLead.firstName);
  });
});