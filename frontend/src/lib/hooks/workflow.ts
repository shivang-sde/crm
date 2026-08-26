import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { workflowApi } from "@/lib/api/workflow";
import { dealCustomFieldApi } from "@/lib/api/deal-custom-fields";
import { dealStageApi } from "@/lib/api/deal-stages";
import { leadApi } from "@/lib/api/leads";
import { userApi } from "@/lib/api/users";
import { api } from "@/lib/api/client";
import { ApiResponse } from "@/types/auth";
import {
  HttpConnectionOption,
  WorkflowCreateRequest,
  WorkflowEdgeRequest,
  WorkflowMetadataResponse,
  WorkflowNodeRequest,
  WorkflowExecutionStatus,
  WorkflowVersionCreateRequest,
} from "@/types/workflow";

export const workflowKeys = {
  all: ["workflows"] as const,
  metadata: () => [...workflowKeys.all, "metadata"] as const,
  httpConnections: () => [...workflowKeys.all, "http-connections"] as const,
  referenceData: (entityType: string) =>
    [...workflowKeys.all, "reference", entityType] as const,
  lists: () => [...workflowKeys.all, "list"] as const,
  list: (params: { page?: number; size?: number }) =>
    [...workflowKeys.lists(), params] as const,
  details: () => [...workflowKeys.all, "detail"] as const,
  detail: (workflowId: string) => [...workflowKeys.details(), workflowId] as const,
  versions: (workflowId: string) =>
    [...workflowKeys.all, "versions", workflowId] as const,
  version: (versionId: string) => [...workflowKeys.all, "version", versionId] as const,
  graph: (versionId: string) => [...workflowKeys.all, "graph", versionId] as const,
  executions: (params: Record<string, unknown>) =>
    [...workflowKeys.all, "executions", params] as const,
  executionDetail: (executionId: string) =>
    [...workflowKeys.all, "executions", "detail", executionId] as const,
};

export function useWorkflows(params: { page?: number; size?: number } = {}) {
  return useQuery({
    queryKey: workflowKeys.list(params),
    queryFn: () => workflowApi.listWorkflows(params),
  });
}

export function useWorkflowHttpConnections() {
  return useQuery<HttpConnectionOption[]>({
    queryKey: workflowKeys.httpConnections(),
    queryFn: () => workflowApi.listHttpConnections(),
  });
}

export function useCreateHttpConnection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      name: string;
      authType: string;
      credential?: Record<string, string>;
      active?: boolean;
    }) => workflowApi.createHttpConnection(data),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: workflowKeys.httpConnections() }),
  });
}

export function useUpdateHttpConnection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      ...data
    }: {
      id: string;
      name?: string;
      authType?: string;
      credential?: Record<string, string>;
      active?: boolean;
    }) => workflowApi.updateHttpConnection(id, data),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: workflowKeys.httpConnections() }),
  });
}

export function useDeleteHttpConnection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => workflowApi.deleteHttpConnection(id),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: workflowKeys.httpConnections() }),
  });
}

export function useTestHttpConnection() {
  return useMutation({
    mutationFn: ({ id, url }: { id: string; url: string }) =>
      workflowApi.testHttpConnection(id, url),
  });
}

export function useWorkflow(workflowId?: string) {
  return useQuery({
    queryKey: workflowKeys.detail(workflowId ?? ""),
    queryFn: () => workflowApi.getWorkflow(workflowId ?? ""),
    enabled: Boolean(workflowId),
  });
}

export function useWorkflowVersions(workflowId?: string) {
  return useQuery({
    queryKey: workflowKeys.versions(workflowId ?? ""),
    queryFn: () => workflowApi.listVersions(workflowId ?? ""),
    enabled: Boolean(workflowId),
  });
}

export function useWorkflowVersion(versionId?: string) {
  return useQuery({
    queryKey: workflowKeys.version(versionId ?? ""),
    queryFn: () => workflowApi.getVersion(versionId ?? ""),
    enabled: Boolean(versionId),
  });
}

export function useWorkflowGraph(versionId?: string) {
  return useQuery({
    queryKey: workflowKeys.graph(versionId ?? ""),
    queryFn: () => workflowApi.getGraph(versionId ?? ""),
    enabled: Boolean(versionId),
  });
}

export function useCreateWorkflow() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (data: WorkflowCreateRequest) => workflowApi.createWorkflow(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: workflowKeys.lists() });
    },
  });
}

/**
 * Authoritative workflow vocabulary (entities, events, metadata fields,
 * context fields, actions, operators). Static per backend deployment.
 */
export function useWorkflowMetadata() {
  return useQuery({
    queryKey: workflowKeys.metadata(),
    queryFn: () => workflowApi.getMetadata(),
    staleTime: Infinity,
  });
}

export interface WorkflowValueOption {
  value: string;
  label: string;
}

export interface WorkflowReferenceData {
  loaded: boolean;
  /** Controlled value options keyed by stable context field name. */
  optionsByField: Record<string, WorkflowValueOption[]>;
  customFieldKeys: string[];
}

const EMPTY_REFERENCE: WorkflowReferenceData = {
  loaded: false,
  optionsByField: {},
  customFieldKeys: [],
};

interface CustomFieldLike {
  fieldKey?: string;
  key?: string;
  isActive?: boolean | null;
}

function extractCustomFieldKeys(fields: CustomFieldLike[] | undefined): string[] {
  return (fields ?? [])
    .filter((field) => field.isActive !== false)
    .map((field) => field.fieldKey ?? field.key ?? "")
    .filter((key) => key !== "");
}

async function fetchContactCustomFields(): Promise<
  { fieldKey?: string; isActive?: boolean | null }[]
> {
  const response = await api.get<ApiResponse<{ fieldKey?: string; isActive?: boolean | null }[]>>(
    "/contact-custom-fields"
  );
  return response.data?.data ?? [];
}

async function fetchAccountCustomFields(): Promise<
  { fieldKey?: string; isActive?: boolean | null }[]
> {
  const response = await api.get<ApiResponse<{ fieldKey?: string; isActive?: boolean | null }[]>>(
    "/account-custom-fields"
  );
  return response.data?.data ?? [];
}

/**
 * Tenant-specific reference values for controlled condition/action fields,
 * resolved from the tenant's own configuration endpoints. Never hardcoded.
 */
export function useWorkflowReferenceData(entityType: string): WorkflowReferenceData {
  return useEntityTypeReferenceData(entityType, Boolean(entityType));
}

/**
 * Reference data for controlled one-hop relationships declared by an entity.
 * Returns data keyed by relationship key (e.g. "account", "convertedContact").
 */
export function useWorkflowRelationshipReferenceData(
  relationships?: Array<{ key: string; relatedEntityType: string | null }>
): Record<string, WorkflowReferenceData> {
  const wants = (type: string) =>
    Boolean(relationships?.some((rel) => rel.relatedEntityType === type));

  const lead = useEntityTypeReferenceData("LEAD", wants("LEAD"));
  const contact = useEntityTypeReferenceData("CONTACT", wants("CONTACT"));
  const account = useEntityTypeReferenceData("ACCOUNT", wants("ACCOUNT"));
  const deal = useEntityTypeReferenceData("DEAL", wants("DEAL"));

  const result: Record<string, WorkflowReferenceData> = {};
  for (const rel of relationships ?? []) {
    if (!rel.relatedEntityType) continue;
    switch (rel.relatedEntityType) {
      case "LEAD": result[rel.key] = lead; break;
      case "CONTACT": result[rel.key] = contact; break;
      case "ACCOUNT": result[rel.key] = account; break;
      case "DEAL": result[rel.key] = deal; break;
    }
  }
  return result;
}

function useEntityTypeReferenceData(
  entityType: string,
  enabled: boolean
): WorkflowReferenceData {
  const leadStatuses = useQuery({
    queryKey: [...workflowKeys.referenceData("LEAD"), "statuses"],
    queryFn: () => leadApi.listStatuses(),
    enabled: enabled && entityType === "LEAD",
    staleTime: 5 * 60 * 1000,
  });
  const leadSources = useQuery({
    queryKey: [...workflowKeys.referenceData("LEAD"), "sources"],
    queryFn: () => leadApi.listSources(),
    enabled: enabled && entityType === "LEAD",
    staleTime: 5 * 60 * 1000,
  });
  const leadCustomFields = useQuery({
    queryKey: [...workflowKeys.referenceData("LEAD"), "custom"],
    queryFn: () => leadApi.listCustomFields(),
    enabled: enabled && entityType === "LEAD",
    staleTime: 5 * 60 * 1000,
  });
  const dealStages = useQuery({
    queryKey: [...workflowKeys.referenceData("DEAL"), "stages"],
    queryFn: () => dealStageApi.listStages(),
    enabled: enabled && entityType === "DEAL",
    staleTime: 5 * 60 * 1000,
  });
  const dealCustomFields = useQuery({
    queryKey: [...workflowKeys.referenceData("DEAL"), "custom"],
    queryFn: () => dealCustomFieldApi.listCustomFields(),
    enabled: enabled && entityType === "DEAL",
    staleTime: 5 * 60 * 1000,
  });
  const contactCustomFields = useQuery({
    queryKey: [...workflowKeys.referenceData("CONTACT"), "custom"],
    queryFn: fetchContactCustomFields,
    enabled: enabled && entityType === "CONTACT",
    staleTime: 5 * 60 * 1000,
  });
  const accountCustomFields = useQuery({
    queryKey: [...workflowKeys.referenceData("ACCOUNT"), "custom"],
    queryFn: fetchAccountCustomFields,
    enabled: enabled && entityType === "ACCOUNT",
    staleTime: 5 * 60 * 1000,
  });
  const users = useQuery({
    queryKey: [...workflowKeys.referenceData("USERS"), "active"],
    queryFn: () =>
      userApi.getUsers({ page: 0, isActive: true }),
    enabled,
    staleTime: 5 * 60 * 1000,
  });

  if (!enabled || !users.data) {
    return EMPTY_REFERENCE;
  }

  const optionsByField: Record<string, WorkflowValueOption[]> = {};

  const userOptions: WorkflowValueOption[] = (users.data.content ?? []).map(
    (user: { id?: string; firstName?: string; lastName?: string; email?: string }) => ({
      value: String(user.id ?? ""),
      label:
        [user.firstName, user.lastName].filter(Boolean).join(" ") ||
        String(user.email ?? user.id),
    })
  );
  if (userOptions.length > 0) {
    optionsByField["entity.ownerId"] = userOptions;
    optionsByField["trigger.metadata.previousOwnerId"] = userOptions;
    optionsByField["trigger.metadata.newOwnerId"] = userOptions;
  }

  if (entityType === "LEAD") {
    const statusById = (leadStatuses.data ?? []).map((status) => ({
      value: String(status.id),
      label: String(status.name),
    }));
    const statusByName = (leadStatuses.data ?? []).map((status) => ({
      value: String(status.name),
      label: String(status.name),
    }));
    if (statusById.length > 0) {
      optionsByField["trigger.metadata.newStatusId"] = statusById;
      optionsByField["trigger.metadata.previousStatusId"] = statusById;
      optionsByField["entity.statusId"] = statusById;
      optionsByField["trigger.metadata.newStatus"] = statusByName;
      optionsByField["trigger.metadata.previousStatus"] = statusByName;
      optionsByField["entity.status"] = statusByName;
    }
    const sourceById = (leadSources.data ?? []).map((source) => ({
      value: String(source.id),
      label: String(source.name),
    }));
    const sourceByName = (leadSources.data ?? []).map((source) => ({
      value: String(source.name),
      label: String(source.name),
    }));
    if (sourceById.length > 0) {
      optionsByField["entity.sourceId"] = sourceById;
      optionsByField["entity.source"] = sourceByName;
    }
  }

  if (entityType === "DEAL") {
    const stageById = (dealStages.data ?? []).map((stage) => ({
      value: String(stage.id),
      label: String(stage.name),
    }));
    const stageByName = (dealStages.data ?? []).map((stage) => ({
      value: String(stage.name),
      label: String(stage.name),
    }));
    if (stageById.length > 0) {
      optionsByField["entity.stageId"] = stageById;
      optionsByField["trigger.metadata.newStageId"] = stageById;
      optionsByField["trigger.metadata.previousStageId"] = stageById;
      optionsByField["entity.stage"] = stageByName;
      optionsByField["trigger.metadata.newStage"] = stageByName;
      optionsByField["trigger.metadata.previousStage"] = stageByName;
    }
  }

  let customKeys: string[] = [];
  if (entityType === "LEAD") customKeys = extractCustomFieldKeys(leadCustomFields.data);
  else if (entityType === "DEAL") customKeys = extractCustomFieldKeys(dealCustomFields.data);
  else if (entityType === "CONTACT") customKeys = extractCustomFieldKeys(contactCustomFields.data);
  else if (entityType === "ACCOUNT") customKeys = extractCustomFieldKeys(accountCustomFields.data);

  return {
    loaded: true,
    optionsByField,
    customFieldKeys: customKeys,
  };
}

export function useCreateWorkflowVersion(workflowId: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (data: WorkflowVersionCreateRequest) =>
      workflowApi.createVersion(workflowId, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: workflowKeys.versions(workflowId) });
      qc.invalidateQueries({ queryKey: workflowKeys.lists() });
      qc.invalidateQueries({ queryKey: workflowKeys.detail(workflowId) });
    },
  });
}

function invalidateGraph(qc: ReturnType<typeof useQueryClient>, versionId: string) {
  qc.invalidateQueries({ queryKey: workflowKeys.graph(versionId) });
}

export function useCreateWorkflowNode(versionId: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (data: WorkflowNodeRequest) => workflowApi.createNode(versionId, data),
    onSuccess: () => invalidateGraph(qc, versionId),
  });
}

export function useUpdateWorkflowNode(versionId: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: ({ nodeId, data }: { nodeId: string; data: WorkflowNodeRequest }) =>
      workflowApi.updateNode(versionId, nodeId, data),
    onSuccess: (_data, variables) => {
      invalidateGraph(qc, versionId);
      void variables;
    },
  });
}

export function useDeleteWorkflowNode(versionId: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (nodeId: string) => workflowApi.deleteNode(versionId, nodeId),
    onSuccess: () => invalidateGraph(qc, versionId),
  });
}

export function useCreateWorkflowEdge(versionId: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (data: WorkflowEdgeRequest) => workflowApi.createEdge(versionId, data),
    onSuccess: () => invalidateGraph(qc, versionId),
  });
}

export function useUpdateWorkflowEdge(versionId: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: ({ edgeId, data }: { edgeId: string; data: WorkflowEdgeRequest }) =>
      workflowApi.updateEdge(versionId, edgeId, data),
    onSuccess: () => invalidateGraph(qc, versionId),
  });
}

export function useDeleteWorkflowEdge(versionId: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (edgeId: string) => workflowApi.deleteEdge(versionId, edgeId),
    onSuccess: () => invalidateGraph(qc, versionId),
  });
}

export function useValidateWorkflowVersion(versionId: string) {
  return useMutation({
    mutationFn: () => workflowApi.validateVersion(versionId),
  });
}

export function useActivateWorkflowVersion(workflowId: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (versionId: string) => workflowApi.activateVersion(versionId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: workflowKeys.versions(workflowId) });
      qc.invalidateQueries({ queryKey: workflowKeys.detail(workflowId) });
      qc.invalidateQueries({ queryKey: workflowKeys.lists() });
    },
  });
}

export function useDeactivateWorkflow(workflowId: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => workflowApi.deactivateWorkflow(id || workflowId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: workflowKeys.detail(workflowId) });
      qc.invalidateQueries({ queryKey: workflowKeys.versions(workflowId) });
      qc.invalidateQueries({ queryKey: workflowKeys.lists() });
    },
  });
}

export function useWorkflowExecutions(
  params: {
    status?: WorkflowExecutionStatus;
    workflowId?: string;
    entityType?: string;
    entityId?: string;
    page?: number;
    size?: number;
  } = {}
) {
  return useQuery({
    queryKey: workflowKeys.executions(params),
    queryFn: () => workflowApi.listExecutions(params),
  });
}

export function useWorkflowExecution(executionId?: string) {
  return useQuery({
    queryKey: workflowKeys.executionDetail(executionId ?? ""),
    queryFn: () => workflowApi.getExecution(executionId ?? ""),
    enabled: Boolean(executionId),
  });
}

export function useReplayWorkflowExecution() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (executionId: string) =>
      workflowApi.replayExecution(executionId),
    onSuccess: (_data, executionId) => {
      qc.invalidateQueries({ queryKey: [...workflowKeys.all, "executions"] });
      void executionId;
    },
  });
}
export function useRetryWorkflowExecution() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (executionId: string) =>
      workflowApi.retryExecution(executionId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [...workflowKeys.all, "executions"] });
    },
  });
}