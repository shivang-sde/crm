export interface HttpConnectionOption {
  id: string;
  name: string;
  authType: string;
  active: boolean;
  credentialConfigured: boolean;
  createdAt: string;
  updatedAt: string;
}

export type WorkflowStatus = "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";

export type WorkflowVersionStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";

export type WorkflowNodeType =
  | "TRIGGER"
  | "CONDITION"
  | "ACTION"
  | "END"
  | "WAIT"
  | "BRANCH";

export type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonValue[]
  | { [key: string]: JsonValue };

export type JsonObject = { [key: string]: JsonValue };

export interface WorkflowResponse {
  id: string;
  name: string;
  status: WorkflowStatus;
  activeVersionId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface WorkflowVersionResponse {
  id: string;
  workflowId: string;
  versionNumber: number;
  status: WorkflowVersionStatus;
  triggerEntityType: string;
  triggerEventType: string;
  createdAt: string;
  updatedAt: string;
}

export type WorkflowNodeConfiguration = JsonObject;

export type WorkflowEdgeConfiguration = JsonObject;

export interface WorkflowGraphNodeResponse {
  id: string;
  nodeKey: string;
  nodeType: WorkflowNodeType;
  name: string;
  configuration: WorkflowNodeConfiguration;
  createdAt: string;
  updatedAt: string;
}

export interface WorkflowGraphEdgeResponse {
  id: string;
  sourceNodeId: string;
  targetNodeId: string;
  edgeKey: string | null;
  configuration: WorkflowEdgeConfiguration;
  createdAt: string;
  updatedAt: string;
}

export interface WorkflowGraphResponse {
  version: WorkflowVersionResponse;
  nodes: WorkflowGraphNodeResponse[];
  edges: WorkflowGraphEdgeResponse[];
}

export interface WorkflowListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface WorkflowCreateRequest {
  name: string;
}

export interface WorkflowVersionCreateRequest {
  triggerEntityType: string;
  triggerEventType: string;
}

export interface WorkflowNodeRequest {
  nodeKey: string;
  nodeType: WorkflowNodeType;
  name: string;
  // Backend JSONB accepts arbitrary JSON objects.
  configuration: Record<string, unknown>;
}

export interface WorkflowEdgeRequest {
  sourceNodeId: string;
  targetNodeId: string;
  edgeKey?: string | null;
  // Backend JSONB accepts arbitrary JSON objects.
  configuration?: Record<string, unknown> | null;
}

export interface WorkflowValidationIssue {
  code: string;
  message: string;
  nodeId?: string | null;
  nodeKey?: string | null;
  edgeId?: string | null;
}

export type WorkflowExecutionStatus =
  | "PENDING"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED";

export type WorkflowNodeExecutionStatus =
  | "PENDING"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "SKIPPED";

export interface WorkflowExecutionSummaryResponse {
  id: string;
  workflowId: string;
  workflowVersionId: string;
  entityType: string;
  entityId: string;
  eventType: string;
  status: WorkflowExecutionStatus;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
  errorCode: string | null;
  errorMessage: string | null;
}

export interface WorkflowExecutionNodeExecutionResponse {
  id: string;
  nodeId: string;
  nodeKey: string;
  nodeType: WorkflowNodeType;
  status: WorkflowNodeExecutionStatus;
  attemptCount: number | null;
  startedAt: string | null;
  completedAt: string | null;
  nextAttemptAt: string | null;
  inputContext?: Record<string, unknown> | null;
  outputContext?: Record<string, unknown> | null;
  lastErrorCode: string | null;
  lastErrorMessage: string | null;
}

export interface WorkflowExecutionDetailResponse {
  id: string;
  workflowId: string;
  workflowVersionId: string;
  entityType: string;
  entityId: string;
  eventType: string;
  status: WorkflowExecutionStatus;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
  attemptCount: number | null;
  lastHeartbeatAt: string | null;
  lastErrorCode: string | null;
  lastErrorMessage: string | null;
  replayedFromExecutionId: string | null;
  causedByExecutionId: string | null;
  causedByEventId: string | null;
  chainDepth: number | null;
  nodeExecutions: WorkflowExecutionNodeExecutionResponse[];
}

export interface WorkflowExecutionReplayResponse {
  executionId: string;
  status: WorkflowExecutionStatus;
  replayedFromExecutionId: string;
  causedByExecutionId?: string | null;
  causedByEventId?: string | null;
  chainDepth?: number;
}

export interface WorkflowExecutionControlResponse {
  executionId: string;
  status: WorkflowExecutionStatus;
}
export interface WorkflowEventMetadataInfo {
  eventType: string;
  label: string;
  metadataFields: string[];
}

export interface WorkflowRelationshipMetadata {
  key: string;
  label: string;
  relatedEntityType: string | null;
  fields: string[];
  customFieldsSupported: boolean;
}

export interface WorkflowEntityMetadata {
  entityType: string;
  label: string;
  events: WorkflowEventMetadataInfo[];
  fields: string[];
  customFieldsSupported: boolean;
  relationships: WorkflowRelationshipMetadata[];
}

export interface WorkflowMetadataResponse {
  entities: WorkflowEntityMetadata[];
  actions: string[];
  operators: string[];
}


