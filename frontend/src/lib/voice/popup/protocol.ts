/**
 * VOICE-03 — typed Main CRM <-> Softphone popup protocol (postMessage).
 * VOICE-04 — hardening: popup generation identity + snapshot sequence for
 * stale/duplicate protection, link lifecycle, source validation.
 *
 * SIP.js-independent. Every message is JSON-serializable. Secrets (SIP
 * password, JWT) MUST NEVER appear in any payload defined here — the popup
 * resolves its own SIP secret through the authenticated API (VOICE-02).
 */
import type { AnyVoiceCommand } from '../commands';
import type { VoiceEvent } from '../events';
import type { VoiceStateSnapshot } from '../runtime';
import type { VoiceCall } from '../types';

export const SOFTPHONE_CHANNEL = 'crm-softphone' as const;
export const SOFTPHONE_PROTOCOL_VERSION = 1;
export const SOFTPHONE_WINDOW_NAME = 'CRM_SOFTPHONE';
export const SOFTPHONE_ROUTE = '/softphone';
/**
 * Attach URL for (re)acquiring the popup reference. The constant hash makes
 * repeat acquisition a same-document navigation: the popup is focused and
 * returned WITHOUT reloading, so an active SIP session survives re-attach
 * after CRM reloads or tab switches. Chromium re-navigates (and destroys
 * the session) when the URL differs — never attach with a bare route.
 */
export const SOFTPHONE_POPUP_URL = '/softphone#attach';
export const SOFTPHONE_COMMAND_TIMEOUT_MS = 15_000;
export const SOFTPHONE_HANDSHAKE_TIMEOUT_MS = 10_000;

/**
 * Cross-tab presence (localStorage — presence ONLY, never secrets/state).
 * The popup heartbeats while mounted; tabs refuse to spawn a second SIP
 * runtime when a foreign tab owns a live popup. sessionStorage tab ids
 * survive same-tab reloads, so re-attach after reload keeps working.
 */
export const SOFTPHONE_PRESENCE_KEY = 'crm-softphone-presence';
export const SOFTPHONE_HEARTBEAT_INTERVAL_MS = 2_000;
export const SOFTPHONE_PRESENCE_TTL_MS = 5_000;

export interface SoftphonePresence {
  version: typeof SOFTPHONE_PROTOCOL_VERSION;
  /** Tab id of the owning main window (sessionStorage-scoped), if known. */
  ownerTabId: string | null;
  updatedAt: number;
}

export function readSoftphonePresence(): SoftphonePresence | null {
  if (typeof window === 'undefined' || typeof localStorage === 'undefined') return null;
  try {
    const raw = localStorage.getItem(SOFTPHONE_PRESENCE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as SoftphonePresence;
    if (parsed.version !== SOFTPHONE_PROTOCOL_VERSION) return null;
    return parsed;
  } catch {
    return null;
  }
}

/** This tab's stable id (survives reload, unique per tab). */
export function softphoneTabId(): string | null {
  if (typeof window === 'undefined' || typeof sessionStorage === 'undefined') return null;
  const KEY = 'crm-softphone-tab';
  try {
    let id = sessionStorage.getItem(KEY);
    if (!id) {
      id = typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID()
        : `tab-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
      sessionStorage.setItem(KEY, id);
    }
    return id;
  } catch {
    return null;
  }
}

/** Main CRM -> popup. Voice ops mirror the VOICE-01 command model. */
export type PopupCommandType =
  | 'POPUP_PING'
  | 'HANDSHAKE'
  | 'POPUP_GET_STATE'
  | 'VOICE_CONNECT'
  | 'VOICE_DISCONNECT'
  | 'VOICE_REGISTER'
  | 'VOICE_UNREGISTER'
  | 'VOICE_DIAL'
  | 'VOICE_ANSWER'
  | 'VOICE_REJECT'
  | 'VOICE_HANGUP'
  | 'VOICE_MUTE'
  | 'VOICE_UNMUTE'
  | 'VOICE_HOLD'
  | 'VOICE_RESUME'
  | 'VOICE_TRANSFER';

/** Popup -> main CRM. */
export type PopupEventType =
  | 'POPUP_READY'
  | 'COMMAND_RESULT'
  | 'VOICE_EVENT';

export interface PopupCommandPayloads {
  POPUP_PING: Record<string, never>;
  HANDSHAKE: { connectorInstanceId?: string; senderTabId?: string };
  POPUP_GET_STATE: Record<string, never>;
  VOICE_CONNECT: { connectorInstanceId?: string };
  VOICE_DISCONNECT: Record<string, never>;
  VOICE_REGISTER: Record<string, never>;
  VOICE_UNREGISTER: Record<string, never>;
  VOICE_DIAL: { destination: string; entityType?: VoiceCall['entityType']; entityId?: string | null };
  VOICE_ANSWER: { callId: string };
  VOICE_REJECT: { callId: string };
  VOICE_HANGUP: { callId: string };
  VOICE_MUTE: { callId: string };
  VOICE_UNMUTE: { callId: string };
  VOICE_HOLD: { callId: string };
  VOICE_RESUME: { callId: string };
  VOICE_TRANSFER: { callId: string; destination: string };
}

export interface SoftphoneSnapshot {
  protocolVersion: number;
  /**
   * Ephemeral popup generation identity (random per popup document boot).
   * A reload creates a new identity; the controller uses it to reject
   * stale messages from a dead generation. Never a secret.
   */
  popupInstanceId: string;
  /** Monotonic sequence owned by the popup; guards event/state ordering. */
  sequence: number;
  connectorInstanceId: string | null;
  ready: boolean;
  voice: VoiceStateSnapshot;
}

export interface CommandResultPayload {
  requestId: string;
  ok: boolean;
  /** VoiceCall | VoiceStateSnapshot | SoftphoneSnapshot | null — never secrets. */
  data?: VoiceCall | VoiceStateSnapshot | SoftphoneSnapshot | null;
  error?: { code: string; message: string };
  /** Generation that produced this result; lets the controller drop stale ones. */
  popupInstanceId?: string;
}

export interface VoiceForwardPayload {
  event: VoiceEvent;
  snapshot: SoftphoneSnapshot;
}

export interface ReadyPayload {
  protocolVersion: number;
  popupInstanceId: string;
  supportedCommands: PopupCommandType[];
}

/** Discriminated union over all commands — narrows on `.type` in switches. */
export type SoftphoneCommandMessage = {
  [K in PopupCommandType]: {
    channel: typeof SOFTPHONE_CHANNEL;
    version: typeof SOFTPHONE_PROTOCOL_VERSION;
    kind: 'command';
    type: K;
    requestId: string;
    payload: PopupCommandPayloads[K];
  };
}[PopupCommandType];

export type SoftphoneMessage =
  | SoftphoneCommandMessage
  | {
      channel: typeof SOFTPHONE_CHANNEL;
      version: typeof SOFTPHONE_PROTOCOL_VERSION;
      kind: 'event';
      type: 'POPUP_READY';
      payload: ReadyPayload;
    }
  | {
      channel: typeof SOFTPHONE_CHANNEL;
      version: typeof SOFTPHONE_PROTOCOL_VERSION;
      kind: 'event';
      type: 'VOICE_EVENT';
      payload: VoiceForwardPayload;
    }
  | {
      channel: typeof SOFTPHONE_CHANNEL;
      version: typeof SOFTPHONE_PROTOCOL_VERSION;
      kind: 'result';
      type: 'COMMAND_RESULT';
      payload: CommandResultPayload;
    };

export function isSoftphoneMessage(value: unknown): value is SoftphoneMessage {
  if (typeof value !== 'object' || value === null) return false;
  const msg = value as Record<string, unknown>;
  return (
    msg.channel === SOFTPHONE_CHANNEL &&
    isSupportedProtocolVersion(msg.version) &&
    (msg.kind === 'command' || msg.kind === 'event' || msg.kind === 'result') &&
    typeof msg.type === 'string'
  );
}

/**
 * Structural pre-check for messages whose version is NOT supported:
 * same channel, anything else. Used to reject deterministically instead
 * of hanging until timeout.
 */
export function isWrongVersionMessage(value: unknown): boolean {
  if (typeof value !== 'object' || value === null) return false;
  const msg = value as Record<string, unknown>;
  return (
    msg.channel === SOFTPHONE_CHANNEL &&
    !isSupportedProtocolVersion(msg.version) &&
    typeof msg.type === 'string'
  );
}

/** Expected postMessage origin: strict same-origin, never '*'. */
export function softphoneOrigin(): string | null {
  if (typeof window === 'undefined') return null;
  return window.location.origin;
}

export function isTrustedSoftphoneEvent(event: MessageEvent): boolean {
  const origin = softphoneOrigin();
  return (
    origin !== null &&
    event.origin === origin &&
    isSoftphoneMessage(event.data)
  );
}

/** Map a popup voice command onto the VOICE-01 runtime command model. */
export function toVoiceCommand(msg: SoftphoneCommandMessage): AnyVoiceCommand | null {
  const requestId = msg.requestId;
  switch (msg.type) {
    case 'VOICE_DIAL':
      return {
        type: 'DIAL',
        requestId,
        payload: {
          destination: msg.payload.destination,
          entityType: msg.payload.entityType ?? undefined,
          entityId: msg.payload.entityId ?? undefined,
        },
      };
    case 'VOICE_ANSWER':
      return { type: 'ANSWER', requestId, payload: { callId: msg.payload.callId } };
    case 'VOICE_REJECT':
      return { type: 'REJECT', requestId, payload: { callId: msg.payload.callId } };
    case 'VOICE_HANGUP':
      return { type: 'HANGUP', requestId, payload: { callId: msg.payload.callId } };
    case 'VOICE_MUTE':
      return { type: 'MUTE', requestId, payload: { callId: msg.payload.callId } };
    case 'VOICE_UNMUTE':
      return { type: 'UNMUTE', requestId, payload: { callId: msg.payload.callId } };
    case 'VOICE_HOLD':
      return { type: 'HOLD', requestId, payload: { callId: msg.payload.callId } };
    case 'VOICE_RESUME':
      return { type: 'RESUME', requestId, payload: { callId: msg.payload.callId } };
    case 'VOICE_TRANSFER':
      return {
        type: 'TRANSFER',
        requestId,
        payload: { callId: msg.payload.callId, destination: msg.payload.destination },
      };
    default:
      return null;
  }
}

export const SUPPORTED_POPUP_COMMANDS: PopupCommandType[] = [
  'POPUP_PING',
  'HANDSHAKE',
  'POPUP_GET_STATE',
  'VOICE_CONNECT',
  'VOICE_DISCONNECT',
  'VOICE_REGISTER',
  'VOICE_UNREGISTER',
  'VOICE_DIAL',
  'VOICE_ANSWER',
  'VOICE_REJECT',
  'VOICE_HANGUP',
  'VOICE_MUTE',
  'VOICE_UNMUTE',
  'VOICE_HOLD',
  'VOICE_RESUME',
  'VOICE_TRANSFER',
];

/** Centralized protocol compatibility check (version 1 only, no negotiation). */
export function isSupportedProtocolVersion(version: unknown): boolean {
  return version === SOFTPHONE_PROTOCOL_VERSION;
}

/** Minimal main-side link lifecycle (§7). */
export type SoftphoneLinkState =
  | 'DISCONNECTED'
  | 'CONNECTING'
  | 'HANDSHAKING'
  | 'READY'
  | 'CLOSED';

/**
 * Commands sendable before the link is READY (state discovery + readiness
 * primitives). All VOICE_* call/session commands require READY and are
 * deterministically rejected otherwise — never queued, never dropped
 * silently.
 */
export function isPreReadyCommand(type: PopupCommandType): boolean {
  return type === 'POPUP_PING' || type === 'HANDSHAKE' || type === 'POPUP_GET_STATE';
}

export type SoftphoneErrorCode =
  | 'SOFTPHONE_POPUP_BLOCKED'
  | 'SOFTPHONE_POPUP_UNAVAILABLE'
  | 'SOFTPHONE_PROTOCOL_MISMATCH'
  | 'SOFTPHONE_HANDSHAKE_TIMEOUT'
  | 'SOFTPHONE_COMMAND_TIMEOUT'
  | 'SOFTPHONE_COMMAND_REJECTED';

export class SoftphoneError extends Error {
  readonly code: SoftphoneErrorCode;
  /** Original voice/provider error code when a command was rejected downstream. */
  readonly causeCode?: string;

  constructor(code: SoftphoneErrorCode, message: string, causeCode?: string) {
    super(message);
    this.name = 'SoftphoneError';
    this.code = code;
    this.causeCode = causeCode;
  }
}
