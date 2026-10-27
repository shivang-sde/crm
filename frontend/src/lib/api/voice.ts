import api from './api';
import { unwrapResponse } from './api-utils';
import type { ApiResponse } from '@/types/auth';
import {
  buildSafeSipConfig,
  type GenericSipConnectionConfig,
  type GenericSipResolvedSecret,
} from '@/lib/voice';

/**
 * VOICE-02 — short-lived SIP connection material.
 *
 * Mirrors GET /api/v1/settings/connectors/{id}/sip-connection.
 * The password is ephemeral: split out of the response and handed ONLY to
 * GenericSipAdapter.connect(). Never cached, never stored.
 */
export interface SipConnectionPayload {
  connectorInstanceId: string;
  providerKey: string;
  endpoint: string;
  sipDomain: string;
  username: string;
  password: string;
  iceServers?: unknown;
}

export interface ResolvedSipConnection {
  connection: GenericSipConnectionConfig;
  secret: GenericSipResolvedSecret;
}

/**
 * VOICE-05 — admin Generic SIP connection management.
 *
 * Mirrors GET/PUT /api/v1/voice/connections/generic-sip on
 * CallingAdminController (raw responses, admin:settings enforced
 * server-side). Responses carry presence metadata only — never secrets.
 */
export interface AdminIceServer {
  /** string or string[] server-side; the admin form uses a single URL string. */
  urls: string | string[];
  username?: string | null;
  credentialConfigured: boolean;
}

export interface AdminSipConnectionConfig {
  endpoint: string | null;
  sipDomain: string | null;
  username: string | null;
  iceServers: AdminIceServer[];
}

export interface AdminSipConnection {
  connectorInstanceId: string | null;
  providerKey: string | null;
  connectorName: string | null;
  active: boolean;
  configured: boolean;
  secretConfigured: boolean;
  secretScope: string | null;
  config: AdminSipConnectionConfig;
}

export interface SaveAdminSipConnectionRequest {
  endpoint: string;
  sipDomain: string;
  username: string;
  iceServers: { urls: string; username?: string; credential?: string }[];
  /** Blank/absent = keep the stored secret. Never send masked values. */
  secret?: string;
  removeSecret?: boolean;
}

export const voiceAdminApi = {
  getGenericSipConnection: async (): Promise<AdminSipConnection> => {
    const response = await api.get<AdminSipConnection>(
      '/voice/connections/generic-sip'
    );

    return response.data;
  },

  saveGenericSipConnection: async (
    request: SaveAdminSipConnectionRequest
  ): Promise<AdminSipConnection> => {
    const response = await api.put<AdminSipConnection>(
      '/voice/connections/generic-sip',
      request
    );

    return response.data;
  },
};

export const voiceApi = {
  getSipConnection: async (
    connectorInstanceId: string,
  ): Promise<ResolvedSipConnection> => {
    const response = await api.get<
      ApiResponse<SipConnectionPayload>
    >(
      `/settings/connectors/${connectorInstanceId}/sip-connection`,
    );

    const payload = unwrapResponse(response);

    const connection = buildSafeSipConfig(
      payload.connectorInstanceId,
      {
        endpoint: payload.endpoint,
        sipDomain: payload.sipDomain,
        username: payload.username,
        iceServers: payload.iceServers,
      },
    );

    if (!connection || !payload.password) {
      throw new Error(
        'SIP connection is not fully configured for this connector.',
      );
    }

    return {
      connection,
      secret: { password: payload.password },
    };
  },
};
