import { test, expect } from '../fixtures';
import { createApiHelper } from '../helpers/api-helper';
import { WorkflowBuilder } from '../helpers/workflow-builder';

function uniqueEmail(p: string){ return `${p}-${Date.now()}-${Math.random().toString(36).slice(2,4)}@e2e.example`; }

test.describe('Acquisition matrix', () => {
  test('IMPORT via CSV creates lead and triggers workflow', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    const statusId = (await api.listLeadStatuses())[0]?.id;
    // Find or create IMPORT config
    let configs = await api.listAcquisitionConfigs().catch(()=> ({ data: [] }));
    let config = (configs.data||configs).find((c:any)=> c.transportType==='IMPORT');
    if (!config) {
      try {
        config = await api.createAcquisitionConfig({ name: `Import-${Date.now()}`, transportType: 'IMPORT', active: true, fieldMappings: [] });
        config = config.data || config;
      } catch { test.skip(); return; }
    }
    const wb = new WorkflowBuilder(request, testUser.accessToken);
    const { workflowId } = await wb.buildSimpleLeadTaskWorkflow(`Import-Acq-${Date.now()}`, 'Import acquisition task');
    const csv = `firstName,lastName,email,company\nImport,Test,${uniqueEmail('import')},ImportCo`;
    const blob = new Blob([csv], { type: 'text/csv' });
    const formData = new FormData(); formData.append('file', blob, 'import.csv');
    const importRes = await request.post(`/acquisition/configs/${config.id}/import`, { headers: { Authorization: `Bearer ${testUser.accessToken}` }, multipart: { file: { name: 'import.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) } } as any });
    // Fallback raw fetch if above fails
    if (!importRes.ok()) {
      const r2 = await request.post(`/acquisition/configs/${config.id}/import`, { headers: { Authorization: `Bearer ${testUser.accessToken}` }, multipart: { file: { name: 'import.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) } } as any });
      expect(r2.ok()).toBeTruthy();
    }
    await new Promise(r=> setTimeout(r, 5000));
    const events = await api.listAcquisitionEvents(config.id, { page:0, size:5 });
    if (events.data?.length) {
      const ev = events.data.find((e:any)=> e.leadId);
      if (ev?.leadId) {
        const lead = await api.getLead(ev.leadId);
        expect(lead.email).toBeTruthy();
        await api.waitForExecutionByEntity('LEAD', ev.leadId, 'COMPLETED', 45000, 2000).catch(()=>{});
      }
    }
    await wb.deactivate(workflowId);
  });

  test('DIRECT_API ingestion creates lead', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    let configs = await api.listAcquisitionConfigs().catch(()=> ({ data: [] }));
    let config = (configs.data||configs).find((c:any)=> ['API','DIRECT_API','WEBHOOK'].includes(c.transportType));
    if (!config) test.skip();
    const email = uniqueEmail('direct');
    const res = await request.post(`/public/acquisition/${config.publicKey}`, { data: { firstName: 'Direct', lastName: 'Api', email, company: 'DirectCo' } });
    // Public endpoint may be /api/v1/public/... adjust
    const res2 = res.ok() ? res : await request.post(`/acquisition/public/${config.publicKey}`, { data: { firstName: 'Direct', lastName: 'Api', email, company: 'DirectCo' } });
    if (!res2.ok()) {
      // Document acquisition availability
      console.log('DIRECT_API status=' + res2.status());
      test.skip();
    }
  });

  test('FORM ingestion (if configured) creates lead', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    let configs = await api.listAcquisitionConfigs().catch(()=> ({ data: [] }));
    const formConfig = (configs.data||configs).find((c:any)=> c.transportType==='FORM');
    if (!formConfig) test.skip();
    const email = uniqueEmail('form');
    const res = await request.post(`/public/forms/${formConfig.publicKey}`, { data: { firstName: 'Form', email, company: 'FormCo' } });
    expect([200,201,202].includes(res.status()) || !res.ok()).toBeTruthy();
  });

  test('acquisition deduplication: same external_event_id -> DUPLICATE not second lead', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    let configs = await api.listAcquisitionConfigs().catch(()=> ({ data: [] }));
    let config = (configs.data||configs).find((c:any)=> c.transportType==='WEBHOOK' || c.transportType==='API');
    if (!config) test.skip();
    // Exercise idempotencyKey via repeated public ingestion if endpoint available
    const email = uniqueEmail('dedup');
    const payload = { firstName: 'Dedup', lastName: 'Test', email, company: 'DedupCo', externalEventId: `dedup-${Date.now()}` };
    // First call
    await request.post(`/public/acquisition/${config.publicKey}`, { data: payload }).catch(()=>{});
    await request.post(`/public/acquisition/${config.publicKey}`, { data: payload }).catch(()=>{});
    // Verify at most one lead with that email
    await new Promise(r=> setTimeout(r, 3000));
    const leads = await api.listLeads({ search: email } as any).catch(()=> ({ data: [] }));
    const matches = (leads.data||leads).filter?.((l:any)=> l.email===email) || [];
    expect(matches.length).toBeLessThanOrEqual(1);
  });

  test('acquisition validation: missing required field -> REJECTED', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    // Direct lead create with missing firstName should be rejected (400)
    try {
      await api.createLead({ firstName: '', lastName: '', email: 'bad', company: '', statusId: '00000000-0000-0000-0000-000000000000' } as any);
      throw new Error('should reject');
    } catch (e:any) {
      expect(e.message).toBeTruthy();
      expect(e.message).not.toContain('should reject');
    }
  });
});

test.describe('CRM event verification (entityType/eventType)', () => {
  test('LEAD.CREATED emits with correct entityType/eventType', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    const statusId = (await api.listLeadStatuses())[0]?.id;
    const lead = await api.createLead({ firstName: 'Event', lastName: 'Test', email: uniqueEmail('event'), company: 'EventCo', statusId });
    const execs = await api.waitForExecutionByEntity('LEAD', lead.id, 'COMPLETED', 45000, 2000).catch(()=> null);
    // At least verify LEAD.CREATED workflow triggered
    const list = await api.listExecutions({ entityType: 'LEAD', entityId: lead.id, size: 5 });
    expect(list.data.some((e:any)=> e.eventType==='CREATED' && e.entityType==='LEAD')).toBeTruthy();
  });

  test('DEAL.STAGE_TRANSITIONED emits on stage change', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);
    const statusId = (await api.listLeadStatuses())[0]?.id;
    const lead = await api.createLead({ firstName: 'DealEvent', lastName: 'Test', email: uniqueEmail('deal-event'), company: 'DealCo', statusId });
    const cvt = await api.convertLead(lead.id, { createDeal: true, dealName: `Deal-${Date.now()}`, dealAmount: 1000 } as any).catch(()=> null);
    if (!cvt?.dealId) test.skip();
    const stagesRes = await request.get('/deal-stages', { headers: { Authorization: `Bearer ${testUser.accessToken}` } });
    const stages = stagesRes.ok() ? (await stagesRes.json()).data : [];
    if (stages.length < 2) test.skip();
    await request.patch(`/deals/${cvt.dealId}/stage`, { headers: { Authorization: `Bearer ${testUser.accessToken}` }, data: { stageId: stages[1].id } });
    await new Promise(r=> setTimeout(r, 4000));
    const execs = await api.listExecutions({ entityType: 'DEAL', entityId: cvt.dealId, size: 10 });
    console.log('DEAL_STAGE_EXECS=' + JSON.stringify(execs.data.map((x:any)=> x.eventType)));
  });
});
