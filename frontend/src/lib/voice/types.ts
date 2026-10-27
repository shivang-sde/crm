/**
 * VOICE-01 — provider-neutral voice domain types.
 *
 * Architecture-only. No database, no SIP/WebRTC implementation.
 * No SIP.js / JsSIP / RTCPeerConnection / MediaStream types may leak
 * through these public contracts.
 */

export const GENERIC_SIP_PROVIDER_KEY = 'GENERIC_SIP' as const;

export type VoiceProviderKey = typeof GENERIC_SIP_PROVIDER_KEY | (string & {});

/** §5 — provider-neutral registration states. */
export type VoiceRegistrationState =
  | 'DISCONNECTED'
  | 'CONNECTING'
  | 'REGISTERING'
  | 'REGISTERED'
  | 'RECONNECTING'
  | 'FAILED';

export interface VoiceRegistrationSnapshot {
  state: VoiceRegistrationState;
  registered: boolean;
  providerKey: string;
  connectionId: string | null;
  lastError: string | null;
  /** ISO-8601 timestamp of last state transition. */
  changedAt: string;
  /**
   * VOICE-07 additive recovery metadata (ephemeral, never secrets).
   * Present only while a reconnect loop is armed.
   */
  retryCount?: number;
  /** ISO-8601 timestamp of the next scheduled reconnect attempt, if any. */
  nextRetryAt?: string | null;
  /** Stable machine-readable reason for the last unexpected disconnect. */
  lastDisconnectReason?: string | null;
}

/** §6 — provider-neutral call states. */
export type VoiceCallState =
  | 'IDLE'
  | 'DIALING'
  | 'RINGING'
  | 'CONNECTED'
  | 'HELD'
  | 'ENDING'
  | 'ENDED'
  | 'FAILED';

export type VoiceCallDirection = 'INBOUND' | 'OUTBOUND';

/** Future CRM linkage. No CRM call entities are created in this phase. */
export type VoiceEntityType =
  | 'LEAD'
  | 'CONTACT'
  | 'ACCOUNT'
  | 'DEAL'
  | 'RECORD';

export interface VoiceCall {
  callId: string;
  direction: VoiceCallDirection;
  /** Destination / phone number. */
  destination: string;
  state: VoiceCallState;
  /** ISO-8601 timestamps. */
  startedAt: string;
  connectedAt: string | null;
  endedAt: string | null;
  muted: boolean;
  held: boolean;
  entityType: VoiceEntityType | null;
  entityId: string | null;
}

/** §7 — network states (VOICE-07 resilience hook). */
export type VoiceNetworkState =
  | 'ONLINE'
  | 'OFFLINE'
  | 'RECONNECTING'
  | 'UNKNOWN';

/**
 * §3 — safe Generic SIP connection description.
 *
 * Contains NO secret. The SIP password/secret is resolved separately from
 * the existing connector/credential infrastructure (see `./sip-config`)
 * and passed only to `VoiceProviderAdapter.connect()` at connect time.
 */
export interface IceServerConfig {
  urls: string | string[];
  username?: string;
  /** NOTE: per-call TURN credential only — never a persisted SIP secret. */
  credential?: string;
}

export interface GenericSipConnectionConfig {
  connectionId: string;
  providerKey: typeof GENERIC_SIP_PROVIDER_KEY;
  /** SIP over WebSocket endpoint, e.g. wss://pbx.example.com:8089/ws */
  endpoint: string;
  sipDomain: string;
  /** SIP username / extension. */
  username: string;
  iceServers?: IceServerConfig[];
}

/**
 * Resolved SIP secret. Ephemeral by contract: held only in memory by the
 * future SIP adapter for the duration of registration, never persisted to
 * localStorage/sessionStorage, never emitted in events/state/logs/URLs.
 */
export interface GenericSipResolvedSecret {
  password: string;
}

export interface VoiceConnectOptions {
  connection: GenericSipConnectionConfig;
  /** Present only at connect time; never stored on snapshots/events. */
  secret?: GenericSipResolvedSecret;
}

export interface VoiceCallMetadata {
  entityType?: VoiceEntityType;
  entityId?: string;
}

/** §12 — provider-neutral error model. */
export type VoiceErrorCode =
  | 'VOICE_NOT_CONFIGURED'
  | 'VOICE_CONNECTION_FAILED'
  | 'VOICE_REGISTRATION_FAILED'
  | 'VOICE_CALL_FAILED'
  | 'VOICE_CALL_NOT_FOUND'
  | 'VOICE_OPERATION_NOT_SUPPORTED'
  | 'VOICE_NETWORK_ERROR'
  | 'VOICE_PROVIDER_ERROR'
  | 'VOICE_UNKNOWN_ERROR'
  /** VOICE-04 additive: authenticated session required (no secret detail). */
  | 'VOICE_AUTH_REQUIRED'
  /** VOICE-07 additive: browser reports offline; recovery deferred, not failed. */
  | 'VOICE_NETWORK_OFFLINE';

export class VoiceError extends Error {
  readonly code: VoiceErrorCode;
  readonly retryable: boolean;

  constructor(code: VoiceErrorCode, message: string, retryable = false) {
    super(message);
    this.name = 'VoiceError';
    this.code = code;
    this.retryable = retryable;
  }
}
