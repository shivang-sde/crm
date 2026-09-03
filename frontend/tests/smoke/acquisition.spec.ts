import { test, expect } from '../fixtures';
import { createApiHelper } from '../helpers/api-helper';
import { apiUrl } from '../helpers/api-url';

test.describe('@smoke Lead Acquisition -> Workflow', () => {
  test('webhook acquisition creates lead and triggers workflow', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);

    const configsResponse = await request.get(apiUrl('/acquisition/configs'), {
      headers: { Authorization: `Bearer ${testUser.accessToken}` },
    });
    expect(configsResponse.ok()).toBeTruthy();

    const configsBody = await configsResponse.json();
    expect(configsBody.success).toBe(true);

    if (!configsBody.data || configsBody.data.length === 0) {
      test.skip();
      return;
    }

    const config = configsBody.data[0];
    const configId = config.id;

    const triggerResponse = await api.triggerPolling(configId);
    expect(triggerResponse.success).toBe(true);

    await new Promise(resolve => setTimeout(resolve, 5000));

    const eventsResponse = await api.listAcquisitionEvents(configId, { page: 0, size: 5 });
    expect(eventsResponse.success).toBe(true);

    if (eventsResponse.data && eventsResponse.data.length > 0) {
      const latestEvent = eventsResponse.data[0];
      expect(latestEvent.status).toBeTruthy();

      if (latestEvent.leadId) {
        const lead = await api.getLead(latestEvent.leadId);
        expect(lead.id).toBe(latestEvent.leadId);

        const execution = await api.waitForExecutionByEntity(
          'lead',
          latestEvent.leadId,
          'COMPLETED',
          60000,
          3000
        );
        expect(execution.status).toBe('COMPLETED');
      }
    }
  });

  test('CSV import acquisition creates lead', async ({ request, testUser }) => {
    const api = createApiHelper(request, testUser.accessToken);

    const configsResponse = await request.get(apiUrl('/acquisition/configs'), {
      headers: { Authorization: `Bearer ${testUser.accessToken}` },
    });
    expect(configsResponse.ok()).toBeTruthy();

    const configsBody = await configsResponse.json();
    if (!configsBody.data || configsBody.data.length === 0) {
      test.skip();
      return;
    }

    const config = configsBody.data[0];
    const configId = config.id;

    const csvContent = `firstName,lastName,email,phone,company
CSV,Import,Test,csv-import-${Date.now()}@example.com,+15559998888,CSV Import Company`;

    const formData = new FormData();
    const blob = new Blob([csvContent], { type: 'text/csv' });
    formData.append('file', blob, 'test-import.csv');

    const importResponse = await request.post(apiUrl(`/acquisition/configs/${configId}/import`), {
      headers: { Authorization: `Bearer ${testUser.accessToken}` },
      multipart: formData,
    });
    expect(importResponse.ok()).toBeTruthy();

    const importBody = await importResponse.json();
    expect(importBody.success).toBe(true);
    expect(importBody.data?.imported).toBeGreaterThan(0);

    await new Promise(resolve => setTimeout(resolve, 3000));

    const eventsResponse = await api.listAcquisitionEvents(configId, { page: 0, size: 5 });
    if (eventsResponse.data && eventsResponse.data.length > 0) {
      const latestEvent = eventsResponse.data[0];
      if (latestEvent.leadId) {
        const lead = await api.getLead(latestEvent.leadId);
        expect(lead.email).toContain('csv-import-');
      }
    }
  });
});