import { APIRequestContext } from '@playwright/test';
import { LeadCreateRequest, LeadResponse, LeadListParams } from '@/types/leads';
import { WorkflowExecutionStatus, WorkflowExecutionSummaryResponse, WorkflowExecutionDetailResponse } from '@/types/workflow';
import { TenantProvisionRequest, TenantProvisionResponse } from '@/types/tenant';
import { apiUrl } from './api-url';

interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: { code?: string; message?: string };
}

function unwrapResponse<T>(response: ApiResponse<T>): T {
  if (!response.success || response.data === undefined) {
    throw new Error(response.error?.message || 'API request failed');
  }
  return response.data;
}

type HttpMethod = 'get' | 'post' | 'put' | 'delete';

export class ApiHelper {
  constructor(
    private request: APIRequestContext,
    private accessToken?: string
  ) {}

  private async requestWithAuth<T>(
    method: HttpMethod,
    url: string,
    options?: { data?: unknown; params?: Record<string, string | number | boolean> }
  ): Promise<T> {
    const fullUrl = url.startsWith('http') ? url : apiUrl(url);
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    };
    if (this.accessToken) {
      headers['Authorization'] = `Bearer ${this.accessToken}`;
    }

    const response = await this.request[method](fullUrl, {
      headers,
      data: options?.data,
      params: options?.params as Record<string, string | number | boolean> | undefined,
    });

    const body = (await response.json()) as ApiResponse<T>;
    if (!response.ok() || !body.success) {
      throw new Error(body.error?.message || `${method.toUpperCase()} ${url} failed: ${response.status()}`);
    }
    return unwrapResponse(body);
  }

  setAuth(token: string): void {
    this.accessToken = token;
  }

  clearAuth(): void {
    this.accessToken = undefined;
  }

  // Auth
  async login(email: string, password: string) {
    return this.requestWithAuth<{ accessToken: string; user: any; tenant?: any }>('post', '/auth/login', {
      data: { email, password },
    });
  }

  async getCurrentUser() {
    return this.requestWithAuth<any>('get', '/auth/me');
  }

  // Tenants
  async provisionTenant(data: TenantProvisionRequest): Promise<TenantProvisionResponse> {
    return this.requestWithAuth<TenantProvisionResponse>('post', '/tenants/provision', { data });
  }

  async getTenant(tenantId: string) {
    return this.requestWithAuth<any>('get', `/tenants/${tenantId}`);
  }

  // Leads
  async createLead(data: LeadCreateRequest): Promise<LeadResponse> {
    return this.requestWithAuth<LeadResponse>('post', '/leads', { data });
  }

  async getLead(id: string): Promise<LeadResponse> {
    return this.requestWithAuth<LeadResponse>('get', `/leads/${id}`);
  }

  async listLeads(params: LeadListParams = {}): Promise<{ data: LeadResponse[]; meta: any }> {
    const searchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) searchParams.set(key, String(value));
    });
    const response = await this.requestWithAuth<any>('get', `/leads?${searchParams.toString()}`);
    return response;
  }

  async updateLead(id: string, data: Partial<LeadCreateRequest>): Promise<LeadResponse> {
    return this.requestWithAuth<LeadResponse>('put', `/leads/${id}`, { data });
  }

  async deleteLead(id: string): Promise<void> {
    await this.requestWithAuth<void>('delete', `/leads/${id}`);
  }

  // Workflows
  async listExecutions(params: {
    status?: WorkflowExecutionStatus;
    workflowId?: string;
    entityType?: string;
    entityId?: string;
    page?: number;
    size?: number;
  } = {}): Promise<{ data: WorkflowExecutionSummaryResponse[]; meta: any }> {
    const searchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) searchParams.set(key, String(value));
    });
    const fullUrl = apiUrl(`/workflows/executions?${searchParams.toString()}`);
    const headers: Record<string,string> = { 'Content-Type':'application/json', Accept:'application/json' };
    if (this.accessToken) headers.Authorization = `Bearer ${this.accessToken}`;
    const response = await this.request.get(fullUrl, { headers });
    const body = await response.json() as ApiResponse<WorkflowExecutionSummaryResponse[]> & { meta?: any };
    if (!response.ok() || !body.success) throw new Error((body as any).error?.message || `GET /workflows/executions ${response.status()}`);
    return { data: body.data, meta: (body as any).meta };
  }

  async getExecution(executionId: string): Promise<WorkflowExecutionDetailResponse> {
    return this.requestWithAuth<WorkflowExecutionDetailResponse>('get', `/workflows/executions/${executionId}`);
  }

  async waitForExecution(
    executionId: string,
    expectedStatus: WorkflowExecutionStatus = 'COMPLETED',
    timeoutMs = 30000,
    pollIntervalMs = 2000
  ): Promise<WorkflowExecutionDetailResponse> {
    const startTime = Date.now();
    while (Date.now() - startTime < timeoutMs) {
      const execution = await this.getExecution(executionId);
      if (execution.status === expectedStatus) {
        return execution;
      }
      if (execution.status === 'FAILED') {
        throw new Error(`Workflow execution ${executionId} ended with status ${execution.status}`);
      }
      await new Promise(resolve => setTimeout(resolve, pollIntervalMs));
    }
    const lastExecution = await this.getExecution(executionId);
    throw new Error(
      `Timed out waiting for WorkflowExecution status=${expectedStatus}. ` +
      `Last observed status=${lastExecution.status}. Execution ID=${executionId}`
    );
  }

  async waitForExecutionByEntity(
    entityType: string,
    entityId: string,
    expectedStatus: WorkflowExecutionStatus = 'COMPLETED',
    timeoutMs = 30000,
    pollIntervalMs = 2000
  ): Promise<WorkflowExecutionDetailResponse> {
    const startTime = Date.now();
    while (Date.now() - startTime < timeoutMs) {
      const executions = await this.listExecutions({ entityType, entityId, size: 1 });
      if (executions.data.length > 0) {
        const execution = executions.data[0];
        if (execution.status === expectedStatus) {
          return this.getExecution(execution.id);
        }
        if (execution.status === 'FAILED') {
          throw new Error(`Workflow execution ${execution.id} ended with status ${execution.status}`);
        }
      }
      await new Promise(resolve => setTimeout(resolve, pollIntervalMs));
    }
    throw new Error(
      `Timed out waiting for WorkflowExecution for ${entityType}:${entityId} to reach ${expectedStatus}`
    );
  }

  // Contacts / Accounts / Deals / Tasks (minimal for lifecycle)
  async createContact(data: any): Promise<any> { return this.requestWithAuth<any>('post', '/contacts', { data }); }
  async getContact(id: string): Promise<any> { return this.requestWithAuth<any>('get', `/contacts/${id}`); }
  async createAccount(data: any): Promise<any> { return this.requestWithAuth<any>('post', '/accounts', { data }); }
  async getAccount(id: string): Promise<any> { return this.requestWithAuth<any>('get', `/accounts/${id}`); }
  async createDeal(data: any): Promise<any> { return this.requestWithAuth<any>('post', '/deals', { data }); }
  async getDeal(id: string): Promise<any> { return this.requestWithAuth<any>('get', `/deals/${id}`); }
  async convertLead(leadId: string, data: any): Promise<any> { return this.requestWithAuth<any>('post', `/leads/${leadId}/convert`, { data }); }
  async listTasks(params: Record<string, string|number|boolean> = {}): Promise<any> {
    const qs = new URLSearchParams(); Object.entries(params).forEach(([k,v])=> qs.set(k,String(v)));
    return this.requestWithAuth<any>('get', `/tasks?${qs.toString()}`);
  }
  async createTask(data: any): Promise<any> { return this.requestWithAuth<any>('post', '/tasks', { data }); }
  async getTask(id: string): Promise<any> { return this.requestWithAuth<any>('get', `/tasks/${id}`); }
  async listLeadStatuses(): Promise<any[]> { return this.requestWithAuth<any[]>('get', '/lead-statuses'); }
  async listLeadSources(): Promise<any[]> { return this.requestWithAuth<any[]>('get', '/lead-sources'); }
  async createLeadStatus(data: any): Promise<any> { return this.requestWithAuth<any>('post', '/lead-statuses', { data }); }
  async createLeadSource(data: any): Promise<any> { return this.requestWithAuth<any>('post', '/lead-sources', { data }); }
  async listCustomFields(): Promise<any[]> { return this.requestWithAuth<any[]>('get', '/lead-custom-fields'); }
  async createCustomField(data: any): Promise<any> { return this.requestWithAuth<any>('post', '/lead-custom-fields', { data }); }
  // Workflow extras
  async listWorkflows(params: Record<string, string|number|boolean> = {}): Promise<any> {
    const qs = new URLSearchParams(); Object.entries(params).forEach(([k,v])=> qs.set(k,String(v)));
    return this.requestWithAuth<any>('get', `/workflows?${qs.toString()}`);
  }
  async getWorkflow(id: string): Promise<any> { return this.requestWithAuth<any>('get', `/workflows/${id}`); }
  async getGraph(versionId: string): Promise<any> { return this.requestWithAuth<any>('get', `/workflows/versions/${versionId}/graph`); }
  async retryExecution(executionId: string): Promise<any> { return this.requestWithAuth<any>('post', `/workflows/executions/${executionId}/retry`); }
  async replayExecution(executionId: string): Promise<any> { return this.requestWithAuth<any>('post', `/workflows/executions/${executionId}/replay`); }
  // Acquisition
  async createAcquisitionConfig(data: any) {
    return this.requestWithAuth<any>('post', '/acquisition/configs', { data });
  }
  async listAcquisitionConfigs(): Promise<any> { return this.requestWithAuth<any>('get', '/acquisition/configs'); }
  async getAcquisitionConfig(id: string): Promise<any> { return this.requestWithAuth<any>('get', `/acquisition/configs/${id}`); }
  async triggerPolling(configId: string) {
    return this.requestWithAuth<any>('post', `/acquisition/configs/${configId}/polling/trigger`);
  }
  async listAcquisitionEvents(configId: string, params: Record<string, string | number | boolean | undefined> = {}) {
    const searchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) searchParams.set(key, String(value));
    });
    return this.requestWithAuth<any>('get', `/acquisition/configs/${configId}/events?${searchParams.toString()}`);
  }
  async createHttpConnection(data: any): Promise<any> { return this.requestWithAuth<any>('post', '/workflows/http-connections', { data }); }
  async listHttpConnections(): Promise<any> { return this.requestWithAuth<any>('get', '/workflows/http-connections'); }
  // Raw request helper for security tests (returns raw response)
  async rawRequest(method: 'get'|'post'|'put'|'delete', url: string, opts?: { data?: unknown; params?: Record<string,string|number|boolean>}) {
    const fullUrl = url.startsWith('http') ? url : apiUrl(url);
    const headers: Record<string,string> = { 'Content-Type':'application/json', Accept:'application/json' };
    if (this.accessToken) headers.Authorization = `Bearer ${this.accessToken}`;
    return this.request[method](fullUrl, { headers, data: opts?.data, params: opts?.params });
  }
}

export function createApiHelper(request: APIRequestContext, accessToken?: string): ApiHelper {
  return new ApiHelper(request, accessToken);
}