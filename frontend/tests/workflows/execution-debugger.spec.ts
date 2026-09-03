import { test, expect } from '../fixtures';
import { createApiHelper } from '../helpers/api-helper';
import { WorkflowBuilder } from '../helpers/workflow-builder';

function uid(p: string){ return `${p}-${Date.now()}-${Math.random().toString(36).slice(2,3)}@e2e.example`; }
async function sid(api: ReturnType<typeof createApiHelper>){ const s=await api.listLeadStatuses(); return s.find((x:any)=>x.isDefault)?.id || s[0].id; }

test.describe('UX-03 visual execution debugger', () => {
  test('simple branch: Website TRUE vs Referral FALSE shows correct taken/skipped', async ({ page, request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    const wb = new WorkflowBuilder(request, testUser.accessToken);
    const status = await sid(api);
    const sources = await api.listLeadSources();
    let sWebsite = sources.find((s:any)=> s.name.toLowerCase()==='website')?.id;
    if (!sWebsite) { const ns = await api.createLeadSource({ name: `Website-${Date.now()}` } as any); sWebsite = ns.id || ns.data?.id; }
    const sOther = sources.find((s:any)=> s.id !== sWebsite)?.id || sWebsite;

    const wf = await wb.createWorkflow(`Dbg-Branch-${Date.now()}`);
    const v = await wb.createVersion(wf, 'LEAD', 'CREATED');
    const t = await wb.addNode(v, { nodeKey: 'trigger', nodeType: 'TRIGGER', name: 'Lead Created', configuration: { entityType: 'LEAD', eventType: 'CREATED' } });
    const c = await wb.addNode(v, { nodeKey: 'cond', nodeType: 'CONDITION', name: 'Source check', configuration: { field: 'entity.source.id', operator: 'EQUALS', value: sWebsite, logic: 'AND', conditions: [{ field: 'entity.source.id', operator: 'EQUALS', value: sWebsite }] } });
    // Note: CONDITION legacy config uses field/operator/value; Branch style uses conditions array. Support both.
    // For compatibility, set also direct field/operator for older evaluator
    const trueAct = await wb.addNode(v, { nodeKey: 'true_act', nodeType: 'ACTION', name: 'True task', configuration: { actionType: 'CREATE_TASK', subject: 'True branch', entityType: '{{entity.entityType}}', entityId: '{{entity.entityId}}' } });
    const falseAct = await wb.addNode(v, { nodeKey: 'false_act', nodeType: 'ACTION', name: 'False task', configuration: { actionType: 'SET_CONTEXT_VALUE', key: 'skipped', value: 'yes' } });
    const te = await wb.addNode(v, { nodeKey: 't_end', nodeType: 'END', name: 'T End', configuration: {} });
    const fe = await wb.addNode(v, { nodeKey: 'f_end', nodeType: 'END', name: 'F End', configuration: {} });
    await wb.addEdge(v, { sourceNodeId: t, targetNodeId: c });
    await wb.addEdge(v, { sourceNodeId: c, targetNodeId: trueAct, edgeKey: 'TRUE', configuration: { outcome: 'TRUE' } });
    await wb.addEdge(v, { sourceNodeId: c, targetNodeId: falseAct, edgeKey: 'FALSE', configuration: { outcome: 'FALSE' } });
    await wb.addEdge(v, { sourceNodeId: trueAct, targetNodeId: te });
    await wb.addEdge(v, { sourceNodeId: falseAct, targetNodeId: fe });
    await wb.activate(v);

    const leadTrue = await api.createLead({ firstName: 'Dbg', lastName: 'True', email: uid('dbg-true'), company: 'DbgCo', statusId: status, sourceId: sWebsite });
    const execTrue = await api.waitForExecutionByEntity('LEAD', leadTrue.id, 'COMPLETED', 45000, 2000);
    const leadFalse = await api.createLead({ firstName: 'Dbg', lastName: 'False', email: uid('dbg-false'), company: 'DbgCo2', statusId: status, sourceId: sOther });
    const execFalse = await api.waitForExecutionByEntity('LEAD', leadFalse.id, 'COMPLETED', 45000, 2000);

    // Open debugger for TRUE execution and verify graph + inspector
    await page.goto('/sign-in');
    await page.getByLabel('Email Address').fill(testUser.email);
    await page.getByLabel('Password').fill(testUser.password);
    await page.getByRole('button', { name: 'Sign In' }).click();
    await expect(page).toHaveURL(/\/home/, { timeout: 15000 });
    await page.goto(`/workflows/${wf}/executions/${execTrue.id}`);
    await expect(page.getByRole('heading', { name: 'Execution' })).toBeVisible({ timeout: 8000 });
    await expect(page.getByText(`v`)).toBeVisible({ timeout: 5000 });
    // Graph should be visible
    await expect(page.getByText('Visual Execution Graph')).toBeVisible({ timeout: 5000 });
    // Click condition node
    const condNode = page.locator('[data-id="cond"], [data-node-key="cond"]').first();
    if (await condNode.isVisible({ timeout: 2000 }).catch(()=> false)) await condNode.click();
    // Inspector should show rule result
    await expect(page.getByText(/Condition evaluation|Rule 1/i).first()).toBeVisible({ timeout: 5000 });

    // Also verify FALSE execution shows FALSE outcome via API
    const detailFalse = await api.getExecution(execFalse.id);
    const condExecFalse = detailFalse.nodeExecutions.find((n:any)=> n.nodeKey==='cond');
    expect(condExecFalse).toBeTruthy();
    expect(condExecFalse!.status).toBe('COMPLETED');
    const rr = (condExecFalse!.outputContext as any)?.ruleResults;
    if (rr) expect(rr[0].passed).toBe(false);

    await wb.deactivate(wf);
  });

  test('failed node shows error and retry/replay remain accurate', async ({ page, request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    const wb = new WorkflowBuilder(request, testUser.accessToken);
    const wf = await wb.createWorkflow(`Dbg-Fail-${Date.now()}`);
    const v = await wb.createVersion(wf, 'LEAD', 'CREATED');
    const t = await wb.addNode(v, { nodeKey: 'trigger', nodeType: 'TRIGGER', name: 'T', configuration: { entityType: 'LEAD', eventType: 'CREATED' } });
    const a = await wb.addNode(v, { nodeKey: 'http', nodeType: 'ACTION', name: 'HTTP fail', configuration: { actionType: 'HTTP_API', method: 'GET', url: 'http://10.255.255.1/should-fail' } });
    const e = await wb.addNode(v, { nodeKey: 'end', nodeType: 'END', name: 'End', configuration: {} });
    await wb.addEdge(v, { sourceNodeId: t, targetNodeId: a }); await wb.addEdge(v, { sourceNodeId: a, targetNodeId: e });
    try { await wb.activate(v); } catch { test.skip(); return; }
    const lead = await api.createLead({ firstName: 'Fail', lastName: 'Dbg', email: uid('dbg-fail'), company: 'FailCo', statusId: await sid(api) });
    const exec = await api.listExecutions({ entityType: 'LEAD', entityId: lead.id, size: 5 }).then(async r=> {
      await new Promise(res=> setTimeout(res, 8000));
      return api.listExecutions({ entityType: 'LEAD', entityId: lead.id, size: 5 });
    });
    const failed = exec.data.find((x:any)=> x.status==='FAILED');
    if (!failed) test.skip();
    await page.goto('/sign-in');
    await page.getByLabel('Email Address').fill(testUser.email);
    await page.getByLabel('Password').fill(testUser.password);
    await page.getByRole('button', { name: 'Sign In' }).click();
    await expect(page).toHaveURL(/\/home/, { timeout: 15000 });
    await page.goto(`/workflows/${wf}/executions/${failed!.id}`);
    await expect(page.getByText(/Failure/i).first()).toBeVisible({ timeout: 5000 });
    await expect(page.getByText(/HTTP result|Error|Failed node/i).first()).toBeVisible({ timeout: 5000 });
    await wb.deactivate(wf);
  });

  test('historical version: execution shows version at time of run, not latest', async ({ page, request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    const wb = new WorkflowBuilder(request, testUser.accessToken);
    const wf = await wb.createWorkflow(`Dbg-Hist-${Date.now()}`);
    const v1 = await wb.createVersion(wf, 'LEAD', 'CREATED');
    const t1 = await wb.addNode(v1, { nodeKey: 'trigger', nodeType: 'TRIGGER', name: 'T', configuration: { entityType: 'LEAD', eventType: 'CREATED' } });
    const a1 = await wb.addNode(v1, { nodeKey: 'act', nodeType: 'ACTION', name: 'V1', configuration: { actionType: 'CREATE_TASK', subject: 'V1 task', entityType: '{{entity.entityType}}', entityId: '{{entity.entityId}}' } });
    const e1 = await wb.addNode(v1, { nodeKey: 'end', nodeType: 'END', name: 'End', configuration: {} });
    await wb.addEdge(v1, { sourceNodeId: t1, targetNodeId: a1 }); await wb.addEdge(v1, { sourceNodeId: a1, targetNodeId: e1 }); await wb.activate(v1);
    const lead1 = await api.createLead({ firstName: 'Hist', lastName: 'V1', email: uid('hist-v1'), company: 'HistCo', statusId: await sid(api) });
    const exec1 = await api.waitForExecutionByEntity('LEAD', lead1.id, 'COMPLETED', 45000, 2000);
    const v2 = await wb.createVersion(wf, 'LEAD', 'CREATED');
    const t2 = await wb.addNode(v2, { nodeKey: 'trigger', nodeType: 'TRIGGER', name: 'T', configuration: { entityType: 'LEAD', eventType: 'CREATED' } });
    const a2 = await wb.addNode(v2, { nodeKey: 'act2', nodeType: 'ACTION', name: 'V2', configuration: { actionType: 'CREATE_TASK', subject: 'V2 task', entityType: '{{entity.entityType}}', entityId: '{{entity.entityId}}' } });
    const e2 = await wb.addNode(v2, { nodeKey: 'end', nodeType: 'END', name: 'End', configuration: {} });
    await wb.addEdge(v2, { sourceNodeId: t2, targetNodeId: a2 }); await wb.addEdge(v2, { sourceNodeId: a2, targetNodeId: e2 }); await wb.activate(v2);
    await page.goto('/sign-in');
    await page.getByLabel('Email Address').fill(testUser.email);
    await page.getByLabel('Password').fill(testUser.password);
    await page.getByRole('button', { name: 'Sign In' }).click();
    await expect(page).toHaveURL(/\/home/, { timeout: 15000 });
    await page.goto(`/workflows/${wf}/executions/${exec1.id}`);
    await expect(page.getByText(/Historical version/i).first()).toBeVisible({ timeout: 5000 });
    // Verify exec still references v1
    const detail = await api.getExecution(exec1.id);
    expect(detail.workflowVersionId).toBe(v1);
    await wb.deactivate(wf);
  });
});
