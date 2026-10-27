/**
 * VOICE-01 §8 — provider-neutral voice events.
 *
 * All payloads are JSON-serializable (future VOICE-04 popup postMessage).
 * Never provider-specific objects, never credentials/secrets.
 */
import type {
  VoiceCall,
  VoiceNetworkState,
  VoiceRegistrationSnapshot,
} from './types';

export type VoiceEventType =
  | 'REGISTRATION_CHANGED'
  | 'CALL_CREATED'
  | 'CALL_STATE_CHANGED'
  | 'CALL_ENDED'
  | 'NETWORK_CHANGED'
  | 'ERROR';

export interface VoiceEventMap {
  REGISTRATION_CHANGED: VoiceRegistrationSnapshot;
  CALL_CREATED: VoiceCall;
  CALL_STATE_CHANGED: VoiceCall;
  CALL_ENDED: VoiceCall;
  NETWORK_CHANGED: { state: VoiceNetworkState; changedAt: string };
  ERROR: { code: string; message: string; callId?: string };
}

export interface VoiceEvent<T extends VoiceEventType = VoiceEventType> {
  type: T;
  payload: VoiceEventMap[T];
  /** ISO-8601 emission timestamp. */
  emittedAt: string;
}

export type VoiceEventListener<T extends VoiceEventType = VoiceEventType> = (
  event: VoiceEvent<T>,
) => void;

export function createVoiceEvent<T extends VoiceEventType>(
  type: T,
  payload: VoiceEventMap[T],
): VoiceEvent<T> {
  return { type, payload, emittedAt: new Date().toISOString() };
}
