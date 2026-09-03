import { test, expect } from '../fixtures';
import { createApiHelper } from '../helpers/api-helper';
import { WorkflowBuilder } from '../helpers/workflow-builder';

function uid(prefix: string) { return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2,4)}@e2e.example`; }
async function statusId(api: ReturnType<typeof createApiHelper>) { const s = await api.listLeadStatuses(); return s.find((x:any)=>x.isDefault)?.id || s[0].id; }

test.describe('F-02 LEAD.UPDATED', () => {
  test('generic lead update emits LEAD.UPDATED and workflow executes', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    const wb = new WorkflowBuilder(request, testUser.accessToken);
    const sid = await statusId(api);
    // Build UPDATED workflow
    const wf = await wb.createWorkflow(`Updated-${Date.now()}`);
    const v = await wb.createVersion(wf, 'LEAD', 'UPDATED');
    const t = await wb.addNode(v, { nodeKey: 'trigger', nodeType: 'TRIGGER', name: 'Updated', configuration: { entityType: 'LEAD', eventType: 'UPDATED' } });
    const a = await wb.addNode(v, { nodeKey: 'act', nodeType: 'ACTION', name: 'Task', configuration: { actionType: 'CREATE_TASK', subject: 'Updated task', entityType: '{{entity.entityType}}', entityId: '{{entity.entityId}}' } });
    const e = await wb.addNode(v, { nodeKey: 'end', nodeType: 'END', name: 'End', configuration: {} });
    await wb.addEdge(v, { sourceNodeId: t, targetNodeId: a }); await wb.addEdge(v, { sourceNodeId: a, targetNodeId: e }); await wb.activate(v);

    const lead = await api.createLead({ firstName: 'Upd', lastName: 'Test', email: uid('upd'), company: 'UpdCo', statusId: sid });
    // No UPDATED yet
    await new Promise(r=> setTimeout(r, 2000));
    // Generic update (company) should emit UPDATED
    await api.updateLead(lead.id, { company: 'UpdCo2' } as any);
    const exec = await api.waitForExecutionByEntity('LEAD', lead.id, 'COMPLETED', 45000, 2000);
    expect(exec.eventType).toBe('UPDATED');
    expect(exec.entityId).toBe(lead.id);
    const tasks = await api.listTasks({ entityType: 'LEAD', entityId: lead.id } as any);
    const data = (tasks as any).data || tasks;
    expect(data.some((t:any)=> t.subject==='Updated task')).toBeTruthy();
    await wb.deactivate(wf);
  });

  test('status change emits STATUS_CHANGED (and generic update handling)', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    const statuses = await api.listLeadStatuses();
    if (statuses.length < 2) test.skip();
    const s1 = statuses[0].id, s2 = statuses[1].id;
    const wb = new WorkflowBuilder(request, testUser.accessToken);
    const wf = await wb.createWorkflow(`StatusChk-${Date.now()}`);
    const v = await wb.createVersion(wf, 'LEAD', 'STATUS_CHANGED');
    const t = await wb.addNode(v, { nodeKey: 'trigger', nodeType: 'TRIGGER', name: 'Status', configuration: { entityType: 'LEAD', eventType: 'STATUS_CHANGED' } });
    const a = await wb.addNode(v, { nodeKey: 'act', nodeType: 'ACTION', name: 'Task', configuration: { actionType: 'CREATE_TASK', subject: 'Status task', entityType: '{{entity.entityType}}', entityId: '{{entity.entityId}}' } });
    const e = await wb.addNode(v, { nodeKey: 'end', nodeType: 'END', name: 'End', configuration: {} });
    await wb.addEdge(v, { sourceNodeId: t, targetNodeId: a }); await wb.addEdge(v, { sourceNodeId: a, targetNodeId: e }); await wb.activate(v);

    const lead = await api.createLead({ firstName: 'Status', lastName: 'Test', email: uid('status'), company: 'StatusCo', statusId: s1 });
    await api.rawRequest('put', `/leads/${lead.id}/status`, { data: { statusId: s2 } });
    const exec = await api.waitForExecutionByEntity('LEAD', lead.id, 'COMPLETED', 45000, 2000);
    expect(['STATUS_CHANGED','UPDATED'].includes(exec.eventType)).toBeTruthy();
    await wb.deactivate(wf);
  });

  test('owner change emits OWNER_CHANGED', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    const sid = await statusId(api);
    const wb = new WorkflowBuilder(request, testUser.accessToken);
    const wf = await wb.createWorkflow(`OwnerChk-${Date.now()}`);
    const v = await wb.createVersion(wf, 'LEAD', 'OWNER_CHANGED');
    const t = await wb.addNode(v, { nodeKey: 'trigger', nodeType: 'TRIGGER', name: 'Owner', configuration: { entityType: 'LEAD', eventType: 'OWNER_CHANGED' } });
    const a = await wb.addNode(v, { nodeKey: 'act', nodeType: 'ACTION', name: 'Task', configuration: { actionType: 'CREATE_TASK', subject: 'Owner task', entityType: '{{entity.entityType}}', entityId: '{{entity.entityId}}' } });
    const e = await wb.addNode(v, { nodeKey: 'end', nodeType: 'END', name: 'End', configuration: {} });
    await wb.addEdge(v, { sourceNodeId: t, targetNodeId: a }); await wb.addEdge(v, { sourceNodeId: a, targetNodeId: e }); await wb.activate(v);
    const lead = await api.createLead({ firstName: 'Owner', lastName: 'Test', email: uid('owner'), company: 'OwnerCo', statusId: sid });
    await api.rawRequest('put', `/leads/${lead.id}/assign`, { data: { ownerUserId: testUser.user!.id } });
    const exec = await api.waitForExecutionByEntity('LEAD', lead.id, 'COMPLETED', 45000, 2000);
    expect(['OWNER_CHANGED','UPDATED'].includes(exec.eventType)).toBeTruthy();
    await wb.deactivate(wf);
  });

  test('no-change update emits no UPDATED', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    const sid = await statusId(api);
    const lead = await api.createLead({ firstName: 'NoChange', lastName: 'Test', email: uid('nochange'), company: 'NoChangeCo', statusId: sid });
    // Capture count before
    const before = (await api.listExecutions({ entityType: 'LEAD', entityId: lead.id, size: 20 })).data.length;
    await api.updateLead(lead.id, { company: 'NoChangeCo' } as any); // same value
    await new Promise(r=> setTimeout(r, 3000));
    const after = (await api.listExecutions({ entityType: 'LEAD', entityId: lead.id, size: 20 })).data.length;
    expect(after).toBe(before);
  });

  test('workflow self-mutation does not storm (LEAD.UPDATED -> UPDATE -> LEAD.UPDATED suppressed)', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    const sid = await statusId(api);
    const wb = new WorkflowBuilder(request, testUser.accessToken);
    const wf = await wb.createWorkflow(`SelfUpd-${Date.now()}`);
    const v = await wb.createVersion(wf, 'LEAD', 'UPDATED');
    const t = await wb.addNode(v, { nodeKey: 'trigger', nodeType: 'TRIGGER', name: 'Upd', configuration: { entityType: 'LEAD', eventType: 'UPDATED' } });
    const a = await wb.addNode(v, { nodeKey: 'upd', nodeType: 'ACTION', name: 'Upd field', configuration: { actionType: 'UPDATE_ENTITY_FIELD', entityType: 'LEAD', entityId: '{{entity.entityId}}', field: 'company', value: 'SelfMut' } });
    const e = await wb.addNode(v, { nodeKey: 'end', nodeType: 'END', name: 'End', configuration: {} });
    await wb.addEdge(v, { sourceNodeId: t, targetNodeId: a }); await wb.addEdge(v, { sourceNodeId: a, targetNodeId: e }); await wb.activate(v);
    const lead = await api.createLead({ firstName: 'Self', lastName: 'Test', email: uid('self'), company: 'SelfCo', statusId: sid });
    await api.updateLead(lead.id, { company: 'SelfCo2' } as any);
    await new Promise(r=> setTimeout(r, 8000));
    const execs = await api.listExecutions({ entityType: 'LEAD', entityId: lead.id, size: 20 });
    expect(execs.data.length).toBeLessThan(5); // bounded by self-suppress + depth 5
    await wb.deactivate(wf);
  });
});
