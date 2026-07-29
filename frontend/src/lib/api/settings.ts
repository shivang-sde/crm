import api from "./api";
import { unwrapResponse } from "./api-utils";
import type { ApiResponse } from "@/types/auth";

export interface CredentialFieldDefinition {
  key: string;
  label: string;
  type: "text" | "password";
  required: boolean;
}

export interface MyCallingConnector {
  id: string;
  providerKey: string;
  providerName: string;
  connectorName: string;
  environment?: string | null;
  active: boolean;
  credentialFields: CredentialFieldDefinition[];
}

export interface MyCredentialStatus {
  connectorInstanceId: string;
  configured: boolean;
  authType: string;
}

export interface MyAgentMapping {
  id: string;
  connectorInstanceId: string;
  externalAgentId?: string | null;
  externalAgentNumber?: string | null;
  active: boolean;
}

export interface SaveMyCredentialsRequest {
  authType: string;
  values: Record<string, string>;
}

export interface SaveMyAgentMappingRequest {
  externalAgentId?: string | null;
  externalAgentNumber?: string | null;
  active: boolean;
}

export const myCallingSettingsApi = {
  getConnectors: async (): Promise<MyCallingConnector[]> => {
    const response = await api.get<
      ApiResponse<MyCallingConnector[]>
    >("/settings/connectors");

    return unwrapResponse(response);
  },

  getCredentialStatus: async (
    connectorInstanceId: string
  ): Promise<MyCredentialStatus> => {
    const response = await api.get<
      ApiResponse<MyCredentialStatus>
    >(
      `/settings/connectors/${connectorInstanceId}/credential-status`
    );

    return unwrapResponse(response);
  },

  saveCredentials: async (
    connectorInstanceId: string,
    request: SaveMyCredentialsRequest
  ): Promise<MyCredentialStatus> => {
    const response = await api.put<
      ApiResponse<MyCredentialStatus>
    >(
      `/settings/connectors/${connectorInstanceId}/credentials`,
      request
    );

    return unwrapResponse(response);
  },

  deleteCredentials: async (
    connectorInstanceId: string
  ): Promise<void> => {
    const response = await api.delete<ApiResponse<void>>(
      `/settings/connectors/${connectorInstanceId}/credentials`
    );

    unwrapResponse(response);
  },

  getAgentMapping: async (
    connectorInstanceId: string
  ): Promise<MyAgentMapping | null> => {
    const response = await api.get<
      ApiResponse<MyAgentMapping | null>
    >(
      `/settings/connectors/${connectorInstanceId}/agent-mapping`
    );

    return unwrapResponse(response);
  },

  saveAgentMapping: async (
    connectorInstanceId: string,
    request: SaveMyAgentMappingRequest
  ): Promise<MyAgentMapping> => {
    const response = await api.put<
      ApiResponse<MyAgentMapping>
    >(
      `/settings/connectors/${connectorInstanceId}/agent-mapping`,
      request
    );

    return unwrapResponse(response);
  },
};