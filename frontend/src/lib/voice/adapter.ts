/**
 * VOICE-01 §4 — provider-neutral adapter contract.
 *
 * NOT a database entity. Decouples CRM UI code from SIP.js/JsSIP.
 * All inputs/outputs use provider-neutral types only.
 */
import type { VoiceEventListener, VoiceEventType } from './events';
import type {
  VoiceCall,
  VoiceCallMetadata,
  VoiceConnectOptions,
  VoiceRegistrationSnapshot,
} from './types';

export interface VoiceProviderAdapter {
  readonly providerKey: string;

  connect(options: VoiceConnectOptions): Promise<void>;
  disconnect(): Promise<void>;

  register(): Promise<void>;
  unregister(): Promise<void>;

  getRegistrationState(): VoiceRegistrationSnapshot;

  makeCall(destination: string, metadata?: VoiceCallMetadata): Promise<VoiceCall>;
  answerCall(callId: string): Promise<VoiceCall>;
  rejectCall(callId: string): Promise<VoiceCall>;
  hangup(callId: string): Promise<VoiceCall>;
  mute(callId: string): Promise<VoiceCall>;
  unmute(callId: string): Promise<VoiceCall>;
  hold(callId: string): Promise<VoiceCall>;
  resume(callId: string): Promise<VoiceCall>;
  transfer(callId: string, destination: string): Promise<VoiceCall>;

  getCallState(callId: string): VoiceCall | null;
  getActiveCalls(): VoiceCall[];

  on<T extends VoiceEventType>(
    type: T,
    listener: VoiceEventListener<T>,
  ): () => void;

  destroy(): void;
}
