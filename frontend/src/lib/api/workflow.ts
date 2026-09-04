import { api } from "./client";
import { ApiResponse } from "@/types/auth";
import { unwrapResponse, unwrapListResponse } from "./api-utils";
import {
  HttpConnectionOption,
  WorkflowCreateRequest,
  WorkflowExecutionControlResponse,
  WorkflowExecutionDetailResponse,
  WorkflowExecutionReplayResponse,
  WorkflowExecutionStatus,
  WorkflowExecutionSummaryResponse,
  WorkflowEdgeRequest,
  WorkflowGraphResponse,
  WorkflowListMeta,
  WorkflowMetadataResponse,
  WorkflowNodeRequest,
  WorkflowResponse,
  WorkflowValidationIssue,
  WorkflowVersionCreateRequest,
  WorkflowVersionResponse,
} from "@/types/workflow";
import type { CallingProviderOption } from "@/types/call-opening";

export const workflowApi = {
  getMetadata: async () => {
    const response = await api.get<ApiResponse<WorkflowMetadataResponse>>(
      "/workflows/metadata"
    );
    return unwrapResponse(response);
  },

  listHttpConnections: async () => {
    const response = await api.get<ApiResponse<HttpConnectionOption[]>>(
      "/workflows/http-connections"
    );
    return unwrapResponse(response);
  },

  getHttpConnection: async (id: string) => {
    const response = await api.get<ApiResponse<HttpConnectionOption>>(
      `/workflows/http-connections/${id}`
    );
    return unwrapResponse(response);
  },

  createHttpConnection: async (data: {
    name: string;
    authType: string;
    credential?: Record<string, string>;
    active?: boolean;
  }) => {
    const response = await api.post<ApiResponse<HttpConnectionOption>>(
      "/workflows/http-connections",
      data
    );
    return unwrapResponse(response);
  },

  updateHttpConnection: async (
    id: string,
    data: {
      name?: string;
      authType?: string;
      credential?: Record<string, string>;
      active?: boolean;
    }
  ) => {
    const response = await api.put<ApiResponse<HttpConnectionOption>>(
      `/workflows/http-connections/${id}`,
      data
    );
    return unwrapResponse(response);
  },

  deleteHttpConnection: async (id: string) => {
    const response = await api.delete<ApiResponse<string>>(
      `/workflows/http-connections/${id}`
    );
    return unwrapResponse(response);
  },

  testHttpConnection: async (id: string, url: string) => {
    const response = await api.post<
      ApiResponse<{ success: boolean; statusCode: number; message: string }>
    >(`/workflows/http-connections/${id}/test`, { url });
    return unwrapResponse(response);
  },

  listWorkflows: async (params: { page?: number; size?: number } = {}) => {
    const response = await api.get<ApiResponse<WorkflowResponse[]>>("/workflows", {
      params,
    });
    return unwrapListResponse<WorkflowResponse, WorkflowListMeta>(response);
  },

  getWorkflow: async (workflowId: string) => {
    const response = await api.get<ApiResponse<WorkflowResponse>>(
      `/workflows/${workflowId}`
    );
    return unwrapResponse(response);
  },

  listVersions: async (workflowId: string) => {
    const response = await api.get<ApiResponse<WorkflowVersionResponse[]>>(
      `/workflows/${workflowId}/versions`
    );
    return unwrapListResponse<WorkflowVersionResponse>(response);
  },

  getVersion: async (versionId: string) => {
    const response = await api.get<ApiResponse<WorkflowVersionResponse>>(
      `/workflows/versions/${versionId}`
    );
    return unwrapResponse(response);
  },

  getGraph: async (versionId: string) => {
    const response = await api.get<ApiResponse<WorkflowGraphResponse>>(
      `/workflows/versions/${versionId}/graph`
    );
    return unwrapResponse(response);
  },

  createWorkflow: async (data: WorkflowCreateRequest) => {
    const response = await api.post<ApiResponse<string>>("/workflows", data);
    return unwrapResponse(response);
  },

  createVersion: async (workflowId: string, data: WorkflowVersionCreateRequest) => {
    const response = await api.post<ApiResponse<string>>(
      `/workflows/${workflowId}/versions`,
      data
    );
    return unwrapResponse(response);
  },

  updateVersion: async (versionId: string, data: WorkflowVersionCreateRequest) => {
    const response = await api.put<ApiResponse<string>>(
      `/workflows/versions/${versionId}`,
      data
    );
    return unwrapResponse(response);
  },

  createNode: async (versionId: string, data: WorkflowNodeRequest) => {
    const response = await api.post<ApiResponse<string>>(
      `/workflows/versions/${versionId}/nodes`,
      data
    );
    return unwrapResponse(response);
  },

  updateNode: async (versionId: string, nodeId: string, data: WorkflowNodeRequest) => {
    const response = await api.put<ApiResponse<string>>(
      `/workflows/versions/${versionId}/nodes/${nodeId}`,
      data
    );
    return unwrapResponse(response);
  },

  deleteNode: async (versionId: string, nodeId: string): Promise<void> => {
    await api.delete(`/workflows/versions/${versionId}/nodes/${nodeId}`);
  },

  createEdge: async (versionId: string, data: WorkflowEdgeRequest) => {
    const response = await api.post<ApiResponse<string>>(
      `/workflows/versions/${versionId}/edges`,
      data
    );
    return unwrapResponse(response);
  },

  updateEdge: async (versionId: string, edgeId: string, data: WorkflowEdgeRequest) => {
    const response = await api.put<ApiResponse<string>>(
      `/workflows/versions/${versionId}/edges/${edgeId}`,
      data
    );
    return unwrapResponse(response);
  },

  deleteEdge: async (versionId: string, edgeId: string): Promise<void> => {
    await api.delete(`/workflows/versions/${versionId}/edges/${edgeId}`);
  },

  validateVersion: async (versionId: string) => {
    const response = await api.post<ApiResponse<WorkflowValidationIssue[]>>(
      `/workflows/versions/${versionId}/validate`
    );
    return unwrapResponse(response);
  },

  activateVersion: async (versionId: string) => {
    const response = await api.post<ApiResponse<string>>(
      `/workflows/versions/${versionId}/activate`
    );
    return unwrapResponse(response);
  },

  listExecutions: async (
    params: {
      status?: WorkflowExecutionStatus;
      workflowId?: string;
      entityType?: string;
      entityId?: string;
      page?: number;
      size?: number;
    } = {}
  ) => {
    const response = await api.get<ApiResponse<WorkflowExecutionSummaryResponse[]>>(
      "/workflows/executions",
      { params }
    );
    return unwrapListResponse<WorkflowExecutionSummaryResponse, WorkflowListMeta>(response);
  },

  getExecution: async (executionId: string) => {
    const response = await api.get<ApiResponse<WorkflowExecutionDetailResponse>>(
      `/workflows/executions/${executionId}`
    );
    return unwrapResponse(response);
  },

  replayExecution: async (executionId: string) => {
    const response = await api.post<ApiResponse<WorkflowExecutionReplayResponse>>(
      `/workflows/executions/${executionId}/replay`
    );
    return unwrapResponse(response);
  },
  retryExecution: async (executionId: string) => {
    const response = await api.post<ApiResponse<WorkflowExecutionControlResponse>>(
      `/workflows/executions/${executionId}/retry`
    );
    return unwrapResponse(response);
  },
  deactivateWorkflow: async (workflowId: string) => {
    const response = await api.post<ApiResponse<string>>(
      `/workflows/${workflowId}/deactivate`
    );
    return unwrapResponse(response);
  },

  getCallingProviders: async (): Promise<CallingProviderOption[]> => {
    const response = await api.get<ApiResponse<CallingProviderOption[]>>(
      "/calling-providers"
    );
    return unwrapResponse(response);
  },

  getHttpCredentialTenantStatus: async (): Promise<{ configured: boolean; scope: string; ownerUserId: string | null; keys: string[] }> => {
    const response = await api.get<ApiResponse<{ configured: boolean; scope: string; ownerUserId: string | null; keys: string[] }>>(
      "/workflows/http-credentials/tenant/status"
    );
    return unwrapResponse(response);
  },

  putHttpCredentialTenant: async (credential: Record<string, string>): Promise<{ configured: boolean; scope: string; ownerUserId: string | null; keys: string[] }> => {
    const response = await api.put<ApiResponse<{ configured: boolean; scope: string; ownerUserId: string | null; keys: string[] }>>(
      "/workflows/http-credentials/tenant",
      credential
    );
    return unwrapResponse(response);
  },

  getHttpCredentialUserStatus: async (userId: string): Promise<{ configured: boolean; scope: string; ownerUserId: string | null; keys: string[] }> => {
    const response = await api.get<ApiResponse<{ configured: boolean; scope: string; ownerUserId: string | null; keys: string[] }>>(
      `/workflows/http-credentials/user/${userId}/status`
    );
    return unwrapResponse(response);
  },

  putHttpCredentialUser: async (userId: string, credential: Record<string, string>): Promise<{ configured: boolean; scope: string; ownerUserId: string | null; keys: string[] }> => {
    const response = await api.put<ApiResponse<{ configured: boolean; scope: string; ownerUserId: string | null; keys: string[] }>>(
      `/workflows/http-credentials/user/${userId}`,
      credential
    );
    return unwrapResponse(response);
  },

  getHttpCredentialKeys: async (scope?: string, userId?: string): Promise<string[]> => {
    const params: Record<string, string> = {};
    if (scope) params.scope = scope;
    if (userId) params.userId = userId;
    const response = await api.get<ApiResponse<string[]>>(
      "/workflows/http-credentials/keys",
      { params }
    );
    return unwrapResponse(response);
  },

  deleteHttpCredentialTenant: async (): Promise<string> => {
    const response = await api.delete<ApiResponse<string>>("/workflows/http-credentials/tenant");
    return unwrapResponse(response);
  },

  deleteHttpCredentialUser: async (userId: string): Promise<string> => {
    const response = await api.delete<ApiResponse<string>>(`/workflows/http-credentials/user/${userId}`);
    return unwrapResponse(response);
  },
};
