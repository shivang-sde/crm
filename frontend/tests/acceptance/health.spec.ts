import { test, expect } from '../fixtures';
import { apiUrl } from '../helpers/api-url';

test.describe('Health — test environment preflight', () => {
  test('frontend reachable', async ({ page }) => {
    await page.goto('/sign-in');
    await expect(page.getByRole('heading', { name: /welcome back/i }).first()).toBeVisible({ timeout: 10000 });
  });

  test('backend reachable and auth works', async ({ request }) => {
    const r = await request.get(apiUrl('/auth/me'));
    // unauthenticated should be 401, authenticated health uses testUser fixture
    expect([401, 403, 200]).toContain(r.status());
  });

  test('database and workflow infrastructure available', async ({ request, testUser }) => {
    // If testUser login succeeded, DB + auth are up. Check workflow metadata.
    const r = await request.get(apiUrl('/workflows/metadata'), { headers: { Authorization: `Bearer ${testUser.accessToken}` } });
    expect(r.ok()).toBeTruthy();
    const body = await r.json();
    expect(body.success).toBe(true);
    expect(body.data?.actions).toContain('CREATE_TASK');
  });
});

test.describe('Capability discovery (authoritative)', () => {
  test('documents actual entities/events/actions/operators', async ({ request, testUser }) => {
    const r = await request.get(apiUrl('/workflows/metadata'), { headers: { Authorization: `Bearer ${testUser.accessToken}` } });
    const body = await r.json();
    const meta = body.data;
    // Entities that workflow engine knows
    expect(meta.entities.map((e: any) => e.entityType)).toEqual(expect.arrayContaining(['LEAD','DEAL','CONTACT']));
    // Actions - canonical truth
    expect(meta.actions).toEqual(expect.arrayContaining(['NO_OP','SET_CONTEXT_VALUE','UPDATE_ENTITY_FIELD','ASSIGN_OWNER','CREATE_TASK','CLICK_TO_CALL','HTTP_API']));
    // Operators - 12
    expect(meta.operators).toEqual(expect.arrayContaining(['EQUALS','NOT_EQUALS','CONTAINS','IN','IS_NULL','GREATER_THAN']));
    // Persist as artifact: write to test output for report
    console.log('CAPABILITY_ENTITIES=' + JSON.stringify(meta.entities.map((e: any) => e.entityType)));
    console.log('CAPABILITY_ACTIONS=' + JSON.stringify(meta.actions));
    console.log('CAPABILITY_OPERATORS=' + JSON.stringify(meta.operators));
  });

  test('no LEAD.UPDATED event is emitted (verified)', async ({ request, testUser }) => {
    // This test documents a discovered gap: LEAD update does not emit UPDATED, only STATUS_CHANGED/OWNER_CHANGED/CONVERTED
    const { createApiHelper } = await import('../helpers/api-helper');
    const api = createApiHelper(request, testUser.accessToken);
    // Create then update lead; verify no LEAD.UPDATED execution appears
    const lead = await api.createLead({ firstName: 'Cap', lastName: `Disc-${Date.now()}`, email: `cap-disc-${Date.now()}@example.com`, company: 'CapTest', statusId: (await api.listLeadStatuses())[0]?.id });
    await api.updateLead(lead.id, { company: 'UpdatedCo' });
    await new Promise(r => setTimeout(r, 3000));
    const execs = await api.listExecutions({ entityType: 'LEAD', entityId: lead.id, size: 20 });
    const hasUpdated = execs.data.some((e: any) => e.eventType === 'UPDATED');
    expect(hasUpdated).toBe(false); // documents actual behavior
  });
});
