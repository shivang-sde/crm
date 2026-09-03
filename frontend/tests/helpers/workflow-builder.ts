import { APIRequestContext } from '@playwright/test';
import { apiUrl } from './api-url';

// Reuses ApiHelper's response unwrapping; duplicated to avoid circular import
interface ApiResponse<T> { success: boolean; data: T; error?: { code?: string; message?: string } }

async function req<T>(request: APIRequestContext, token: string | undefined, method: 'get'|'post'|'put'|'delete', url: string, data?: unknown): Promise<T> {
  const fullUrl = url.startsWith('http') ? url : apiUrl(url);
  const headers: Record<string,string> = { 'Content-Type':'application/json', Accept:'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const r = await request[method](fullUrl, { headers, data });
  const body = await r.json() as ApiResponse<T>;
  if (!r.ok() || !body.success) throw new Error(body.error?.message || `${method} ${url} ${r.status()} - ${JSON.stringify(body)}`);
  return body.data;
}

// Minimal workflow graph builder for acceptance tests
export class WorkflowBuilder {
  constructor(private request: APIRequestContext, private token?: string) {}
  private api<T>(m: 'get'|'post'|'put'|'delete', url: string, d?: unknown): Promise<T> { return req<T>(this.request, this.token, m, url, d); }

  async createWorkflow(name: string): Promise<string> {
    return this.api<string>('post', '/workflows', { name });
  }
  async createVersion(workflowId: string, entityType: string, eventType: string): Promise<string> {
    return this.api<string>('post', `/workflows/${workflowId}/versions`, { triggerEntityType: entityType, triggerEventType: eventType });
  }
  async addNode(versionId: string, node: { nodeKey: string; nodeType: string; name: string; configuration?: Record<string, unknown> }): Promise<string> {
    return this.api<string>('post', `/workflows/versions/${versionId}/nodes`, node);
  }
  async addEdge(versionId: string, edge: { sourceNodeId: string; targetNodeId: string; edgeKey?: string | null; configuration?: Record<string, unknown> }): Promise<string> {
    return this.api<string>('post', `/workflows/versions/${versionId}/edges`, edge);
  }
  async activate(versionId: string): Promise<void> { await this.api('post', `/workflows/versions/${versionId}/activate`); }
  async deactivate(workflowId: string): Promise<void> { await this.api('post', `/workflows/${workflowId}/deactivate`); }
  async validate(versionId: string): Promise<unknown[]> { return this.api('post', `/workflows/versions/${versionId}/validate`); }
  async getGraph(versionId: string): Promise<any> { return this.api('get', `/workflows/versions/${versionId}/graph`); }
  async deleteWorkflow(workflowId: string) { // no delete endpoint, deactivate instead
    try { await this.deactivate(workflowId); } catch {}
  }
  // High-level: LEAD.CREATED -> CONDITION(field==value) -> ACTION nodes -> END
  async buildLeadCreatedConditionWorkflow(opts: {
    name: string;
    condition: { field: string; operator: string; value: unknown };
    trueActions: Array<{ actionType: string; config: Record<string, unknown> }>;
    falseActions?: Array<{ actionType: string; config: Record<string, unknown> }>;
  }): Promise<{ workflowId: string; versionId: string }> {
    const workflowId = await this.createWorkflow(opts.name);
    const versionId = await this.createVersion(workflowId, 'LEAD', 'CREATED');
    const triggerId = await this.addNode(versionId, { nodeKey: 'trigger', nodeType: 'TRIGGER', name: 'Lead Created', configuration: { entityType: 'LEAD', eventType: 'CREATED' } });
    const condId = await this.addNode(versionId, { nodeKey: 'cond', nodeType: 'CONDITION', name: 'Check condition', configuration: { logic: 'AND', conditions: [{ field: opts.condition.field, operator: opts.condition.operator, value: opts.condition.value }] } });
    // Build TRUE chain
    let prevId = condId;
    let trueNodes: string[] = [];
    for (let i = 0; i < opts.trueActions.length; i++) {
      const a = opts.trueActions[i];
      const nid = await this.addNode(versionId, { nodeKey: `true_action_${i}`, nodeType: 'ACTION', name: `True ${a.actionType} ${i}`, configuration: { actionType: a.actionType, ...a.config } });
      trueNodes.push(nid);
      // first true action connects from condition TRUE
      if (i === 0) await this.addEdge(versionId, { sourceNodeId: condId, targetNodeId: nid, edgeKey: 'TRUE', configuration: { outcome: 'TRUE' } });
      else await this.addEdge(versionId, { sourceNodeId: prevId, targetNodeId: nid });
      prevId = nid;
    }
    const trueEnd = await this.addNode(versionId, { nodeKey: 'true_end', nodeType: 'END', name: 'True End', configuration: {} });
    if (trueNodes.length > 0) await this.addEdge(versionId, { sourceNodeId: prevId, targetNodeId: trueEnd });
    else await this.addEdge(versionId, { sourceNodeId: condId, targetNodeId: trueEnd, edgeKey: 'TRUE', configuration: { outcome: 'TRUE' } });

    // FALSE branch
    const falseEnd = await this.addNode(versionId, { nodeKey: 'false_end', nodeType: 'END', name: 'False End', configuration: {} });
    if (opts.falseActions && opts.falseActions.length > 0) {
      let fPrev = condId;
      for (let i = 0; i < opts.falseActions.length; i++) {
        const a = opts.falseActions[i];
        const nid = await this.addNode(versionId, { nodeKey: `false_action_${i}`, nodeType: 'ACTION', name: `False ${a.actionType} ${i}`, configuration: { actionType: a.actionType, ...a.config } });
        if (i === 0) await this.addEdge(versionId, { sourceNodeId: condId, targetNodeId: nid, edgeKey: 'FALSE', configuration: { outcome: 'FALSE' } });
        else await this.addEdge(versionId, { sourceNodeId: fPrev, targetNodeId: nid });
        fPrev = nid;
        if (i === opts.falseActions.length - 1) await this.addEdge(versionId, { sourceNodeId: nid, targetNodeId: falseEnd });
      }
    } else {
      await this.addEdge(versionId, { sourceNodeId: condId, targetNodeId: falseEnd, edgeKey: 'FALSE', configuration: { outcome: 'FALSE' } });
    }
    await this.addEdge(versionId, { sourceNodeId: triggerId, targetNodeId: condId });
    const issues = await this.validate(versionId) as any[];
    if (issues.length > 0) throw new Error(`Workflow validation failed: ${JSON.stringify(issues)}`);
    await this.activate(versionId);
    return { workflowId, versionId };
  }

  // Simplest: LEAD.CREATED -> CREATE_TASK -> END
  async buildSimpleLeadTaskWorkflow(name: string, subject: string): Promise<{ workflowId: string; versionId: string }> {
    const workflowId = await this.createWorkflow(name);
    const versionId = await this.createVersion(workflowId, 'LEAD', 'CREATED');
    const t = await this.addNode(versionId, { nodeKey: 'trigger', nodeType: 'TRIGGER', name: 'Lead Created', configuration: { entityType: 'LEAD', eventType: 'CREATED' } });
    const a = await this.addNode(versionId, { nodeKey: 'action', nodeType: 'ACTION', name: 'Create Task', configuration: { actionType: 'CREATE_TASK', subject, entityType: '{{entity.entityType}}', entityId: '{{entity.entityId}}' } });
    const e = await this.addNode(versionId, { nodeKey: 'end', nodeType: 'END', name: 'End', configuration: {} });
    await this.addEdge(versionId, { sourceNodeId: t, targetNodeId: a });
    await this.addEdge(versionId, { sourceNodeId: a, targetNodeId: e });
    await this.activate(versionId);
    return { workflowId, versionId };
  }
}
