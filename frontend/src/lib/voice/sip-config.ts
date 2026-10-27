/**
 * VOICE-01 §3 — Generic SIP configuration resolution.
 *
 * CODE ARCHITECTURE ONLY. Reuses the CRM's EXISTING connector/credential
 * infrastructure — no new table, migration, or credential-storage system:
 *
 * - Non-secret connection fields (WSS endpoint, SIP domain, username,
 *   ICE/STUN/TURN) live in the existing `ConnectorInstance.config` jsonb
 *   of the tenant-scoped connector instance whose provider is the existing
 *   `ProviderDefinition` registry (`sellspark_voice` / future GENERIC_SIP).
 * - The SIP secret lives ONLY in the existing `ConnectorCredential`
 *   `encrypted_value` column and is resolved server-side via the existing
 *   `ConnectorCredentialService.resolveCredentialValue()` /
 *   `decryptValue()` (AES via `CredentialEncryptionService`).
 * - The backend exposes the secret to the browser only through the
 *   existing short-lived execution/credential resolution path at connect
 *   time (same trust model as CLICK_TO_CALL `{{credential.*}}` template
 *   resolution in `HttpProviderActionExecutor`); the safe config below is
 *   the only thing kept in runtime state, events, or (future) popup
 *   messages.
 *
 * Shape reference (backend, existing — do NOT duplicate):
 *   ConnectorInstance { tenantId, provider: ProviderDefinition,
 *     connectorName, baseUrl, config: Map<String, Object>, isActive }
 *   ConnectorCredential { tenantId, connectorInstance, credentialName,
 *     authType, credentialScope (TENANT|USER), ownerUserId,
 *     encryptedValue, isActive }
 */
import { GENERIC_SIP_PROVIDER_KEY } from './types';
import type {
  GenericSipConnectionConfig,
  IceServerConfig,
} from './types';

/** Expected keys inside the existing ConnectorInstance.config jsonb. */
export interface GenericSipConnectorConfigShape {
  endpoint?: unknown;
  sipDomain?: unknown;
  username?: unknown;
  iceServers?: unknown;
}

/**
 * Provider key of the canonical Generic SIP connector (Settings → Voice).
 * Mirrors backend `CallingAdminController.GENERIC_SIP_PROVIDER_KEY`.
 * NOTE: this is the backend ProviderDefinition key (`sellspark_voice`), not
 * the adapter's internal `GENERIC_SIP_PROVIDER_KEY` label (`GENERIC_SIP`).
 */
export const GENERIC_SIP_CONNECTOR_PROVIDER_KEY = 'sellspark_voice' as const;

/** Minimal connector shape needed for Generic SIP resolution. */
export interface GenericSipConnectorRef {
  id: string;
  providerKey: string;
  active?: boolean | null;
}

/**
 * Resolve the shared Generic SIP connector explicitly. Never falls back to
 * the first CALLING connector (e.g. click2call): absent means not
 * configured. Returns the connector id or null.
 */
export function resolveGenericSipConnectorId(
  connectors: readonly GenericSipConnectorRef[] | null | undefined,
): string | null {
  if (!connectors) return null;
  for (const connector of connectors) {
    if (!connector || typeof connector.id !== 'string') continue;
    if (connector.active === false) continue;
    if (connector.providerKey === GENERIC_SIP_CONNECTOR_PROVIDER_KEY) return connector.id;
  }
  return null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asNonEmptyString(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function asIceServers(value: unknown): IceServerConfig[] | undefined {
  if (!Array.isArray(value)) return undefined;
  const servers: IceServerConfig[] = [];
  for (const entry of value) {
    if (!isRecord(entry)) continue;
    const urls = Array.isArray(entry.urls)
      ? entry.urls.filter((u): u is string => typeof u === 'string')
      : typeof entry.urls === 'string'
        ? entry.urls
        : null;
    if (!urls || (Array.isArray(urls) && urls.length === 0)) continue;
    const server: IceServerConfig = { urls };
    const username = asNonEmptyString(entry.username);
    // ponytail: TURN credential passed through verbatim; never the SIP secret.
    if (username) server.username = username;
    if (typeof entry.credential === 'string') {
      server.credential = entry.credential;
    }
    servers.push(server);
  }
  return servers.length > 0 ? servers : undefined;
}

/**
 * Build the safe (secret-free) connection config from an existing
 * ConnectorInstance's config jsonb. Returns null when required fields are
 * missing so the caller can raise VOICE_NOT_CONFIGURED.
 */
export function buildSafeSipConfig(
  connectionId: string,
  connectorConfig: GenericSipConnectorConfigShape,
): GenericSipConnectionConfig | null {
  if (!isRecord(connectorConfig)) return null;
  const endpoint = asNonEmptyString(connectorConfig.endpoint);
  const sipDomain = asNonEmptyString(connectorConfig.sipDomain);
  const username = asNonEmptyString(connectorConfig.username);
  if (!endpoint || !sipDomain || !username) return null;

  const config: GenericSipConnectionConfig = {
    connectionId,
    providerKey: GENERIC_SIP_PROVIDER_KEY,
    endpoint,
    sipDomain,
    username,
  };
  const iceServers = asIceServers(connectorConfig.iceServers);
  if (iceServers) config.iceServers = iceServers;
  return config;
}

/** Backend credential name convention for the SIP secret (existing store). */
export const GENERIC_SIP_SECRET_CREDENTIAL_NAME = 'sip_password';
