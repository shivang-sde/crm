export type CallOpeningActionType =
  | "OPEN_PAGE"
  | "OPEN_MODAL"
  | "OPEN_SIDEBAR"
  | "OPEN_CALL_LAYOUT"
  | "NO_ACTION";

export type CallOpeningDisplayMode = "PAGE" | "MODAL" | "SIDEBAR" | "LAYOUT" | "NONE";

export interface CallOpeningInstruction {
  actionType: CallOpeningActionType;
  displayMode?: CallOpeningDisplayMode | null;
  entityType?: string | null;
  entityId?: string | null;
  callId?: string | null;
  externalCallId?: string | null;
  layoutId?: string | null;
  route?: string | null;
  title?: string | null;
  reason?: string | null;
  resolved?: boolean | null;
  metadata?: Record<string, unknown> | null;
}

export interface CallOpeningEvent {
  id: string;
  tenantId?: string | null;
  userId?: string | null;
  agentId?: string | null;
  callId?: string | null;
  externalCallId?: string | null;
  providerKey?: string | null;
  triggerKey?: string | null;
  instruction: CallOpeningInstruction;
  deliveryStatus?: string | null;
  createdAt?: string | null;
  deliveredAt?: string | null;
}

export interface ClickToCallRequest {
  entityType: string;
  entityId: string;
  phoneNumber?: string | null;
  providerKey?: string | null;
  connectorInstanceId?: string | null;
}

export interface CallingProviderOption {
  providerKey: string;
  providerName: string;
  connectorInstanceId: string;
  connectorName: string;
  environment?: string | null;
  active: boolean;
}

export interface ClickToCallResponse {
  callId?: string | null;
  externalCallId?: string | null;
  status?: string | null;
  message?: string | null;
  instruction?: CallOpeningInstruction | null;
}
