/**
 * VOICE-01 §9 — provider-neutral VoiceRuntime.
 *
 * Framework-free (no React): usable from the normal CRM window AND the
 * future standalone Softphone popup (VOICE-04). React components consume
 * the runtime; they never own the SIP/WebRTC lifecycle.
 *
 * The runtime owns no provider logic — it delegates to the injected
 * VoiceProviderAdapter and re-emits serializable events.
 */
import type { VoiceProviderAdapter } from './adapter';
import type { AnyVoiceCommand, VoiceCommand } from './commands';
import type { VoiceEvent, VoiceEventListener, VoiceEventType } from './events';
import { createVoiceEvent } from './events';
import type {
  VoiceCall,
  VoiceConnectOptions,
  VoiceNetworkState,
  VoiceRegistrationSnapshot,
} from './types';
import { VoiceError } from './types';

type ListenerSet = Set<(event: VoiceEvent<VoiceEventType>) => void>;

export interface VoiceStateSnapshot {
  registration: VoiceRegistrationSnapshot;
  activeCalls: VoiceCall[];
  network: VoiceNetworkState;
}

const TERMINAL_CALL_STATES = new Set(['ENDED', 'FAILED']);

export class VoiceRuntime {
  private readonly listeners = new Map<VoiceEventType, ListenerSet>();
  private adapter: VoiceProviderAdapter | null = null;
  private network: VoiceNetworkState = 'UNKNOWN';
  private destroyed = false;
  private readonly forwarders = new Map<VoiceProviderAdapter, () => void>();

  /** Inject the provider adapter (VOICE-02 Generic SIP adapter). */
  initialize(adapter: VoiceProviderAdapter): void {
    this.assertUsable();
    if (this.adapter) {
      throw new VoiceError(
        'VOICE_PROVIDER_ERROR',
        'VoiceRuntime already initialized',
      );
    }
    this.adapter = adapter;
    const offs = (
      [
        'REGISTRATION_CHANGED',
        'CALL_CREATED',
        'CALL_STATE_CHANGED',
        'CALL_ENDED',
        'NETWORK_CHANGED',
        'ERROR',
      ] as const
    ).map((type) =>
      adapter.on(type, (event) => {
        if (type === 'NETWORK_CHANGED') {
          this.network = (
            event as VoiceEvent<'NETWORK_CHANGED'>
          ).payload.state;
        }
        this.emit(event.type, event.payload);
      }),
    );
    this.forwarders.set(adapter, () => offs.forEach((off) => off()));
  }

  async connect(options: VoiceConnectOptions): Promise<void> {
    await this.requireAdapter().connect(options);
  }

  async disconnect(): Promise<void> {
    await this.requireAdapter().disconnect();
  }

  async register(): Promise<void> {
    await this.requireAdapter().register();
  }

  async unregister(): Promise<void> {
    await this.requireAdapter().unregister();
  }

  getRegistrationState(): VoiceRegistrationSnapshot {
    return this.requireAdapter().getRegistrationState();
  }

  getActiveCalls(): VoiceCall[] {
    return this.requireAdapter().getActiveCalls();
  }

  getNetworkState(): VoiceNetworkState {
    return this.network;
  }

  setNetworkState(state: VoiceNetworkState): void {
    this.assertUsable();
    this.network = state;
    this.emit('NETWORK_CHANGED', {
      state,
      changedAt: new Date().toISOString(),
    });
  }

  getState(): VoiceStateSnapshot {
    return {
      registration: this.getRegistrationState(),
      activeCalls: this.getActiveCalls(),
      network: this.network,
    };
  }

  /**
   * Execute a typed future popup command (§11). Returns serializable data.
   * The transport (postMessage RPC) arrives in VOICE-04.
   */
  async executeCommand(
    command: AnyVoiceCommand,
  ): Promise<VoiceCall | VoiceStateSnapshot | null> {
    const adapter = this.requireAdapter();
    switch (command.type) {
      case 'DIAL':
        return adapter.makeCall(command.payload.destination, {
          entityType: command.payload.entityType,
          entityId: command.payload.entityId,
        });
      case 'ANSWER':
        return adapter.answerCall(command.payload.callId);
      case 'REJECT':
        return adapter.rejectCall(command.payload.callId);
      case 'HANGUP':
        return adapter.hangup(command.payload.callId);
      case 'MUTE':
        return adapter.mute(command.payload.callId);
      case 'UNMUTE':
        return adapter.unmute(command.payload.callId);
      case 'HOLD':
        return adapter.hold(command.payload.callId);
      case 'RESUME':
        return adapter.resume(command.payload.callId);
      case 'TRANSFER':
        return adapter.transfer(
          command.payload.callId,
          command.payload.destination,
        );
      case 'GET_STATE':
        return this.getState();
      default:
        throw new VoiceError(
          'VOICE_OPERATION_NOT_SUPPORTED',
          `Unsupported command: ${(command as VoiceCommand).type}`,
        );
    }
  }

  on<T extends VoiceEventType>(
    type: T,
    listener: VoiceEventListener<T>,
  ): () => void {
    this.assertUsable();
    let set = this.listeners.get(type);
    if (!set) {
      set = new Set();
      this.listeners.set(type, set);
    }
    const wrapped = (event: VoiceEvent<VoiceEventType>) => {
      (listener as VoiceEventListener<VoiceEventType>)(event);
    };
    set.add(wrapped);
    return () => {
      set!.delete(wrapped);
    };
  }

  isCallTerminal(call: VoiceCall): boolean {
    return TERMINAL_CALL_STATES.has(call.state);
  }

  destroy(): void {
    if (this.destroyed) return;
    this.destroyed = true;
    if (this.adapter) {
      this.forwarders.get(this.adapter)?.();
      this.forwarders.delete(this.adapter);
      this.adapter.destroy();
      this.adapter = null;
    }
    this.listeners.clear();
    this.emit('NETWORK_CHANGED', {
      state: 'UNKNOWN',
      changedAt: new Date().toISOString(),
    });
  }

  private requireAdapter(): VoiceProviderAdapter {
    this.assertUsable();
    if (!this.adapter) {
      throw new VoiceError(
        'VOICE_NOT_CONFIGURED',
        'VoiceRuntime has no provider adapter. Call initialize() first.',
      );
    }
    return this.adapter;
  }

  private assertUsable(): void {
    if (this.destroyed) {
      throw new VoiceError(
        'VOICE_PROVIDER_ERROR',
        'VoiceRuntime has been destroyed',
      );
    }
  }

  private emit<T extends VoiceEventType>(
    type: T,
    payload: VoiceEvent<T>['payload'],
  ): void {
    const event = createVoiceEvent(
      type,
      payload as Parameters<typeof createVoiceEvent<T>>[1],
    );
    this.listeners.get(type)?.forEach((listener) => listener(event as VoiceEvent<VoiceEventType>));
  }
}
