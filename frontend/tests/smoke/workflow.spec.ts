import { test, expect } from '../fixtures';
import { createApiHelper } from '../helpers/api-helper';
import { apiUrl } from '../helpers/api-url';
import { WorkflowBuilder } from '../helpers/workflow-builder';

test.describe('@smoke Lead -> Workflow -> Task', () => {
  test('create lead triggers active workflow and creates task', async ({ page, testUser, request }) => {
    const api = createApiHelper(request, testUser.accessToken);
    const wb = new WorkflowBuilder(request, testUser.accessToken);
    const { workflowId } = await wb.buildSimpleLeadTaskWorkflow(`Smoke-${Date.now()}`, 'Smoke task');

    await page.goto('/sign-in');
    await page.getByLabel('Email Address').fill(testUser.email);
    await page.getByLabel('Password').fill(testUser.password);
    await page.getByRole('button', { name: 'Sign In' }).click();
    await expect(page).toHaveURL(/\/home/, { timeout: 15000 });

    const testLead = {
      firstName: 'Workflow',
      lastName: `TestLead-${Date.now()}`,
      email: `workflow-lead-${Date.now()}@example.com`,
      company: 'Workflow Test Company',
    };

    await page.goto('/leads/new');
    await page.getByLabel('First Name').fill(testLead.firstName);
    await page.getByLabel('Last Name').fill(testLead.lastName);
    await page.getByLabel('Email').fill(testLead.email);
    await page.getByLabel('Company').fill(testLead.company);

    const statusSelect = page.getByLabel('Status');
    await statusSelect.click();
    await page.getByRole('option', { name: /New/i }).first().click();

    await page.getByRole('button', { name: 'Create Lead' }).click();
    await expect(page).toHaveURL(/\/leads\/[a-f0-9-]+/, { timeout: 10000 });
    const leadId = page.url().split('/leads/')[1];

    const execution = await api.waitForExecutionByEntity(
      'LEAD',
      leadId,
      'COMPLETED',
      60000,
      3000
    );

    expect(execution.status).toBe('COMPLETED');
    expect(execution.entityType).toBe('LEAD');
    expect(execution.entityId).toBe(leadId);

    // Tasks are created via workflow; verify via API (entityType/entityId + subject)
    const tasks = await api.listTasks({ entityType: 'LEAD', entityId: leadId } as any);
    const data = (tasks as any).data || tasks;
    expect(Array.isArray(data)).toBe(true);
    expect(data.length).toBeGreaterThan(0);
    expect(data[0].entityId).toBe(leadId);
    expect(data[0].subject || data[0].title).toBeTruthy();

    await wb.deactivate(workflowId);
  });

  test('workflow execution visible in UI', async ({ page, testUser, request }) => {
    const api = createApiHelper(request, testUser.accessToken);
    const wb = new WorkflowBuilder(request, testUser.accessToken);
    const { workflowId } = await wb.buildSimpleLeadTaskWorkflow(`SmokeUI-${Date.now()}`, 'Smoke UI task');

    await page.goto('/sign-in');
    await page.getByLabel('Email Address').fill(testUser.email);
    await page.getByLabel('Password').fill(testUser.password);
    await page.getByRole('button', { name: 'Sign In' }).click();
    await expect(page).toHaveURL(/\/home/, { timeout: 15000 });

    const testLead = {
      firstName: 'UIWorkflow',
      lastName: `TestLead-${Date.now()}`,
      email: `ui-workflow-${Date.now()}@example.com`,
      company: 'UI Workflow Test Company',
    };

    await page.goto('/leads/new');
    await page.getByLabel('First Name').fill(testLead.firstName);
    await page.getByLabel('Last Name').fill(testLead.lastName);
    await page.getByLabel('Email').fill(testLead.email);
    await page.getByLabel('Company').fill(testLead.company);

    const statusSelect = page.getByLabel('Status');
    await statusSelect.click();
    await page.getByRole('option', { name: /New/i }).first().click();

    await page.getByRole('button', { name: 'Create Lead' }).click();
    await expect(page).toHaveURL(/\/leads\/[a-f0-9-]+/, { timeout: 10000 });
    const leadId = page.url().split('/leads/')[1];

    await api.waitForExecutionByEntity('LEAD', leadId, 'COMPLETED', 60000, 3000);

    await page.goto(`/leads/${leadId}`);
    await expect(page.getByText(/Activity|Timeline|History/i)).toBeVisible({ timeout: 8000 });

    await wb.deactivate(workflowId);
  });
});
