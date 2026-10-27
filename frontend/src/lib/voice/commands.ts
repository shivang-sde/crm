/**
 * VOICE-01 §11 — typed future command contract (VOICE-04 popup RPC).
 *
 * Transport is NOT implemented here. Commands are JSON-serializable and
 * every command carries a requestId for popup request/response correlation.
 */
import type { VoiceCallMetadata } from './types';

export type VoiceCommandType =
  | 'DIAL'
  | 'ANSWER'
  | 'REJECT'
  | 'HANGUP'
  | 'MUTE'
  | 'UNMUTE'
  | 'HOLD'
  | 'RESUME'
  | 'TRANSFER'
  | 'GET_STATE';

export interface VoiceCommandMap {
  DIAL: { destination: string } & VoiceCallMetadata;
  ANSWER: { callId: string };
  REJECT: { callId: string };
  HANGUP: { callId: string };
  MUTE: { callId: string };
  UNMUTE: { callId: string };
  HOLD: { callId: string };
  RESUME: { callId: string };
  TRANSFER: { callId: string; destination: string };
  GET_STATE: Record<string, never>;
}

export interface VoiceCommand<T extends VoiceCommandType = VoiceCommandType> {
  type: T;
  requestId: string;
  payload: VoiceCommandMap[T];
}

export function createVoiceCommand<T extends VoiceCommandType>(
  type: T,
  requestId: string,
  payload: VoiceCommandMap[T],
): VoiceCommand<T> {
  return { type, requestId, payload };
}

/** Discriminated union over all commands — narrows on `.type` in switches. */
export type AnyVoiceCommand = {
  [K in VoiceCommandType]: VoiceCommand<K>;
}[VoiceCommandType];
