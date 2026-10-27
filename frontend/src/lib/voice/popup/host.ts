/**
 * VOICE-03 — popup-side softphone host. OWNS the voice session:
 * VoiceRuntime + GenericSipAdapter + SIP.js + WebSocket + media.
 * VOICE-04 — hardened: per-boot generation identity, monotonic snapshot
 * sequence, version/source validation, auth-error normalization.
 *
 * The host resolves its own SIP secret through the authenticated VOICE-02
 * API (`voiceApi.getSipConnection`). Secrets never cross postMessage.
 * Main CRM windows are views/controllers only.
 */
import { voiceApi } from '@/lib/api/voice';
import { describeAudioElement, type AudioElementReport } from '../audio-diagnostics';
import type { HoldTrace } from '../hold-diagnostics';
import type { AnyVoiceCommand } from '../commands';
import type { VoiceEvent, VoiceEventType } from '../events';
import { GenericSipAdapter } from '../sip/GenericSipAdapter';
import { VoiceError } from '../types';
import type { GenericSipConnectionConfig, GenericSipResolvedSecret } from '../types';
import { VoiceRuntime } from '../runtime';
import {
  SOFTPHONE_CHANNEL,
  SOFTPHONE_HEARTBEAT_INTERVAL_MS,
  SOFTPHONE_PRESENCE_KEY,
  SOFTPHONE_PROTOCOL_VERSION,
  SUPPORTED_POPUP_COMMANDS,
  isSoftphoneMessage,
  isSupportedProtocolVersion,
  softphoneOrigin,
  toVoiceCommand,
  type CommandResultPayload,
  type SoftphoneCommandMessage,
  type SoftphoneMessage,
  type SoftphonePresence,
  type SoftphoneSnapshot,
} from './protocol';

export type HostUiListener = (snapshot: SoftphoneSnapshot) => void;

const TERMINAL_CALL_STATES = new Set(['ENDED', 'FAILED']);

function newInstanceId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `pop-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

function asNonEmpty(value: unknown): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null;
}

function httpStatusOf(error: unknown): number | null {
  if (typeof error !== 'object' || error === null) return null;
  const response = (error as { response?: unknown }).response;
  if (typeof response !== 'object' || response === null) return null;
  const status = (response as { status?: unknown }).status;
  return typeof status === 'number' ? status : null;
}

export class SoftphoneHost {
  /** Ephemeral generation identity: new value per popup document boot. */
  private readonly instanceId = newInstanceId();
  /** Monotonic snapshot sequence owned by this popup generation. */
  private sequence = 0;
  private runtime: VoiceRuntime | null = null;
  private adapter: GenericSipAdapter | null = null;
  private connectorInstanceId: string | null = null;
  private audioElement: HTMLAudioElement | null = null;
  private readonly uiListeners = new Set<HostUiListener>();
  /**
   * Live main-window targets learned from each command's event.source.
   * window.opener goes stale when the CRM reloads, so every handshake /
   * command re-registers its sender; dead entries are pruned on broadcast.
   */
  private readonly mainTargets = new Set<Window>();
  /** Tab id per main target, learned from HANDSHAKE (presence only). */
  private readonly mainTabIds = new Map<Window, string | null>();
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private mounted = false;
  private destroyed = false;
  private offRuntime: Array<() => void> = [];

  get isMounted(): boolean {
    return this.mounted;
  }

  /**
   * Create runtime + adapter exactly once. Safe to call repeatedly;
   * second calls only (re)attach the audio element / connector id.
   */
  mount(options?: {
    connectorInstanceId?: string;
    audioElement?: HTMLAudioElement | null;
    /** FIX-15: `?diag=1` enables the hold/resume re-INVITE trace. */
    diagnostics?: boolean;
  }): SoftphoneSnapshot {
    if (this.destroyed) {
      throw new VoiceError('VOICE_PROVIDER_ERROR', 'Softphone host has been destroyed.');
    }
    if (options?.connectorInstanceId !== undefined) {
      this.connectorInstanceId = options.connectorInstanceId;
    }
    if (options?.audioElement !== undefined) {
      this.audioElement = options.audioElement;
    }
    if (!this.mounted) {
      this.adapter = new GenericSipAdapter();
      if (this.audioElement) this.adapter.setAudioElement(this.audioElement);
      // FIX-15: diagnostics are OFF unless the caller opts in (?diag=1).
      this.adapter.setHoldDiagnosticsEnabled(options?.diagnostics === true);
      this.runtime = new VoiceRuntime();
      this.runtime.initialize(this.adapter);
      this.offRuntime = (
        [
          'REGISTRATION_CHANGED',
          'CALL_CREATED',
          'CALL_STATE_CHANGED',
          'CALL_ENDED',
          'ERROR',
        ] as const
      ).map((type) =>
        (this.runtime as VoiceRuntime).on(type, (event) =>
          this.onRuntimeEvent(type, event),
        ),
      );
      if (typeof window !== 'undefined') {
        window.addEventListener('message', this.onMessage);
        window.addEventListener('pagehide', this.onPageHide);
        window.addEventListener('beforeunload', this.onBeforeUnload);
        this.startHeartbeat();
      }
      this.mounted = true;
    } else if (this.adapter && options?.audioElement !== undefined) {
      this.adapter.setAudioElement(this.audioElement);
    }
    // Announce readiness to possible mains (fire-and-forget).
    this.postToMains({
      channel: SOFTPHONE_CHANNEL,
      version: SOFTPHONE_PROTOCOL_VERSION,
      kind: 'event',
      type: 'POPUP_READY',
      payload: {
        protocolVersion: SOFTPHONE_PROTOCOL_VERSION,
        popupInstanceId: this.instanceId,
        supportedCommands: SUPPORTED_POPUP_COMMANDS,
      },
    });
    return this.snapshot();
  }

  setConnectorInstanceId(connectorInstanceId: string | null): void {
    this.connectorInstanceId = connectorInstanceId;
  }

  /** Local UI controls (popup dials itself — same path as remote commands). */
  async connectLocal(connectorInstanceId?: string): Promise<SoftphoneSnapshot> {
    const id = asNonEmpty(connectorInstanceId) ?? asNonEmpty(this.connectorInstanceId);
    if (!id) throw new VoiceError('VOICE_NOT_CONFIGURED', 'No calling connector selected.');
    this.connectorInstanceId = id;
    const { connection, secret } = await this.resolveSipConnection(id);
    await this.requireRuntime().connect({ connection, secret });
    return this.snapshot();
  }

  async disconnectLocal(): Promise<SoftphoneSnapshot> {
    await this.requireRuntime().disconnect();
    return this.snapshot();
  }

  /** Popup-local controls — same command path remote callers use. */
  async dialLocal(destination: string): Promise<SoftphoneSnapshot> {
    await this.requireRuntime().executeCommand({
      type: 'DIAL',
      requestId: `local-${Date.now()}`,
      payload: { destination },
    });
    return this.snapshot();
  }

  async answerLocal(callId: string): Promise<SoftphoneSnapshot> {
    await this.requireRuntime().executeCommand({ type: 'ANSWER', requestId: `local-${Date.now()}`, payload: { callId } });
    return this.snapshot();
  }

  async rejectLocal(callId: string): Promise<SoftphoneSnapshot> {
    await this.requireRuntime().executeCommand({ type: 'REJECT', requestId: `local-${Date.now()}`, payload: { callId } });
    return this.snapshot();
  }

  async hangupLocal(callId: string): Promise<SoftphoneSnapshot> {
    await this.requireRuntime().executeCommand({ type: 'HANGUP', requestId: `local-${Date.now()}`, payload: { callId } });
    return this.snapshot();
  }

  async muteLocal(callId: string, muted: boolean): Promise<SoftphoneSnapshot> {
    await this.requireRuntime().executeCommand({
      type: muted ? 'MUTE' : 'UNMUTE',
      requestId: `local-${Date.now()}`,
      payload: { callId },
    });
    return this.snapshot();
  }

  async holdLocal(callId: string, held: boolean): Promise<SoftphoneSnapshot> {
    await this.requireRuntime().executeCommand({
      type: held ? 'HOLD' : 'RESUME',
      requestId: `local-${Date.now()}`,
      payload: { callId },
    });
    return this.snapshot();
  }

  async transferLocal(callId: string, destination: string): Promise<SoftphoneSnapshot> {
    await this.requireRuntime().executeCommand({
      type: 'TRANSFER',
      requestId: `local-${Date.now()}`,
      payload: { callId, destination },
    });
    return this.snapshot();
  }

  snapshot(): SoftphoneSnapshot {
    const runtime = this.runtime;
    this.sequence += 1;
    return {
      protocolVersion: SOFTPHONE_PROTOCOL_VERSION,
      popupInstanceId: this.instanceId,
      sequence: this.sequence,
      connectorInstanceId: this.connectorInstanceId,
      ready: this.mounted && !this.destroyed,
      voice: runtime
        ? runtime.getState()
        : { registration: { state: 'DISCONNECTED', registered: false, providerKey: 'GENERIC_SIP', connectionId: null, lastError: null, changedAt: new Date().toISOString() }, activeCalls: [], network: 'UNKNOWN' },
    };
  }

  subscribe(listener: HostUiListener): () => void {
    this.uiListeners.add(listener);
    return () => {
      this.uiListeners.delete(listener);
    };
  }

  /**
   * FIX-9 popup-local audio diagnostics for the ?diag=1 panel.
   * Popup window only: never posted, never persisted, no secrets.
   */
  async getAudioDiagnostics(): Promise<{
    element: AudioElementReport;
    media: Awaited<ReturnType<GenericSipAdapter['getRemoteAudioStats']>>;
  }> {
    const media = this.adapter && !this.destroyed
      ? await this.adapter.getRemoteAudioStats().catch(() => null)
      : null;
    return { element: describeAudioElement(this.audioElement), media };
  }

  /**
   * FIX-15 — bounded, redacted hold/resume re-INVITE traces.
   * Empty unless `?diag=1` enabled diagnostics. Popup window only.
   */
  getHoldDiagnostics(): HoldTrace[] {
    return this.adapter && !this.destroyed ? this.adapter.getHoldDiagnostics() : [];
  }

  destroy(): void {
    if (this.destroyed) return;
    this.destroyed = true;
    this.mounted = false;
    this.stopHeartbeat();
    this.clearPresence();
    if (typeof window !== 'undefined') {
      window.removeEventListener('message', this.onMessage);
      window.removeEventListener('pagehide', this.onPageHide);
      window.removeEventListener('beforeunload', this.onBeforeUnload);
    }
    this.offRuntime.forEach((off) => off());
    this.offRuntime = [];
    this.uiListeners.clear();
    this.audioElement = null;
    this.runtime?.destroy();
    this.runtime = null;
    this.adapter = null;
  }

  // ---------- private ----------

  private requireRuntime(): VoiceRuntime {
    if (!this.runtime) {
      throw new VoiceError('VOICE_NOT_CONFIGURED', 'Softphone runtime is not mounted.');
    }
    return this.runtime;
  }

  private onRuntimeEvent(type: VoiceEventType, event: VoiceEvent): void {
    if (this.destroyed) return;
    const snapshot = this.snapshot();
    if (type !== 'ERROR') {
      this.uiListeners.forEach((listener) => listener(snapshot));
    }
    this.postToMains({
      channel: SOFTPHONE_CHANNEL,
      version: SOFTPHONE_PROTOCOL_VERSION,
      kind: 'event',
      type: 'VOICE_EVENT',
      payload: { event, snapshot },
    });
  }

  private readonly onMessage = (event: MessageEvent): void => {
    const origin = softphoneOrigin();
    if (origin === null || event.origin !== origin) return;
    // Unsupported version on our own channel: deterministic mismatch reply
    // instead of silence (which would only surface as a timeout).
    if (!isSupportedProtocolVersion((event.data as { version?: unknown } | null)?.version)) {
      this.replyVersionMismatch(event);
      return;
    }
    if (!isSoftphoneMessage(event.data)) return;
    const msg = event.data;
    if (msg.kind !== 'command') return;
    // Source gate: HANDSHAKE may arrive from any same-origin window (that is
    // how tabs adopt); every other command requires a learned source
    // (window.opener or a previously handshook main).
    if (msg.type !== 'HANDSHAKE' && !this.isKnownSource(event.source)) return;
    void this.handleCommand(msg, event);
  };

  private isKnownSource(source: unknown): boolean {
    if (!source || typeof source !== 'object') return false;
    if (this.mainTargets.has(source as Window)) return true;
    try {
      return window.opener === source;
    } catch {
      return false;
    }
  }

  private replyVersionMismatch(event: MessageEvent): void {
    const data = event.data as { channel?: unknown; kind?: unknown; requestId?: unknown };
    if (data.channel !== SOFTPHONE_CHANNEL || data.kind !== 'command') return;
    if (typeof data.requestId !== 'string') return;
    const origin = softphoneOrigin();
    const source = event.source as Window | null;
    if (!origin || !source) return;
    try {
      source.postMessage(
        {
          channel: SOFTPHONE_CHANNEL,
          version: SOFTPHONE_PROTOCOL_VERSION,
          kind: 'result',
          type: 'COMMAND_RESULT',
          payload: {
            requestId: data.requestId,
            ok: false,
            error: { code: 'SOFTPHONE_PROTOCOL_MISMATCH', message: 'Softphone protocol version is not supported.' },
            popupInstanceId: this.instanceId,
          },
        } satisfies SoftphoneMessage,
        origin,
      );
    } catch {
      /* best-effort */
    }
  }

  private async handleCommand(msg: SoftphoneCommandMessage, event: MessageEvent): Promise<void> {
    const sender = this.rememberMain(event.source);
    if (sender && msg.type === 'HANDSHAKE' && msg.payload.senderTabId !== undefined) {
      this.mainTabIds.set(sender, msg.payload.senderTabId);
      // Ownership changed: persist immediately (interval timers are
      // throttled in backgrounded popup windows).
      this.writePresence();
    }
    const reply = (payload: CommandResultPayload): void => {
      const origin = softphoneOrigin();
      const source = event.source as Window | null;
      if (!origin || !source) return;
      const response: SoftphoneMessage = {
        channel: SOFTPHONE_CHANNEL,
        version: SOFTPHONE_PROTOCOL_VERSION,
        kind: 'result',
        type: 'COMMAND_RESULT',
        payload: { ...payload, popupInstanceId: this.instanceId },
      };
      try {
        source.postMessage(response, origin);
      } catch {
        /* target gone — runtime stays alive */
      }
    };
    if (this.destroyed) {
      reply({ requestId: msg.requestId, ok: false, error: { code: 'VOICE_PROVIDER_ERROR', message: 'Softphone is closing.' } });
      return;
    }
    try {
      const data = await this.executeCommand(msg);
      reply({ requestId: msg.requestId, ok: true, data });
    } catch (error) {
      const code = error instanceof VoiceError ? error.code : 'VOICE_UNKNOWN_ERROR';
      reply({
        requestId: msg.requestId,
        ok: false,
        error: { code, message: error instanceof Error ? error.message : 'Unknown softphone error.' },
      });
    }
  }

  private async executeCommand(
    msg: SoftphoneCommandMessage,
  ): Promise<CommandResultPayload['data']> {
    const runtime = this.requireRuntime();
    switch (msg.type) {
      case 'POPUP_PING':
        return null;
      case 'HANDSHAKE': {
        const id = msg.payload.connectorInstanceId;
        if (id !== undefined) this.connectorInstanceId = id;
        return this.snapshot();
      }
      case 'POPUP_GET_STATE':
        return this.snapshot();
      case 'VOICE_CONNECT': {
        const id = asNonEmpty(msg.payload.connectorInstanceId) ?? asNonEmpty(this.connectorInstanceId);
        if (!id) throw new VoiceError('VOICE_NOT_CONFIGURED', 'No calling connector selected.');
        this.connectorInstanceId = id;
        const { connection, secret } = await this.resolveSipConnection(id);
        await runtime.connect({ connection, secret });
        return runtime.getState();
      }
      case 'VOICE_DISCONNECT':
        await runtime.disconnect();
        return runtime.getState();
      case 'VOICE_REGISTER':
        await runtime.register();
        return runtime.getState();
      case 'VOICE_UNREGISTER':
        await runtime.unregister();
        return runtime.getState();
      default: {
        const voiceCommand = toVoiceCommand(msg);
        if (!voiceCommand) throw new VoiceError('VOICE_OPERATION_NOT_SUPPORTED', `Unsupported command: ${msg.type}.`);
        this.assertCallPayload(voiceCommand);
        return runtime.executeCommand(voiceCommand);
      }
    }
  }

  /**
   * Resolve SIP connection material through the authenticated VOICE-02 API
   * and normalize transport/auth failures into stable voice errors.
   * The secret never leaves this method except into the adapter.
   */
  private async resolveSipConnection(
    connectorInstanceId: string,
  ): Promise<{ connection: GenericSipConnectionConfig; secret: GenericSipResolvedSecret }> {
    try {
      return await voiceApi.getSipConnection(connectorInstanceId);
    } catch (error) {
      const status = httpStatusOf(error);
      if (status === 401 || status === 403) {
        throw new VoiceError(
          'VOICE_AUTH_REQUIRED',
          'Calling credentials require a valid CRM session. Sign in again and retry.',
        );
      }
      if (status === null || status === 0) {
        throw new VoiceError(
          'VOICE_CONNECTION_FAILED',
          'Unable to reach the CRM server to resolve calling credentials.',
          true,
        );
      }
      if (error instanceof VoiceError) throw error;
      throw new VoiceError('VOICE_CONNECTION_FAILED', 'Unable to resolve calling credentials.', true);
    }
  }

  /** Boundary guard: malformed call payloads become deterministic errors. */
  private assertCallPayload(command: AnyVoiceCommand): void {
    switch (command.type) {
      case 'DIAL':
        if (!asNonEmpty(command.payload.destination)) {
          throw new VoiceError('VOICE_CALL_FAILED', 'Destination is required.');
        }
        break;
      case 'TRANSFER':
        if (!asNonEmpty(command.payload.callId) || !asNonEmpty(command.payload.destination)) {
          throw new VoiceError('VOICE_CALL_FAILED', 'Call and destination are required.');
        }
        break;
      case 'ANSWER':
      case 'REJECT':
      case 'HANGUP':
      case 'MUTE':
      case 'UNMUTE':
      case 'HOLD':
      case 'RESUME':
        if (!asNonEmpty(command.payload.callId)) {
          throw new VoiceError('VOICE_CALL_NOT_FOUND', 'Call not found.');
        }
        break;
      default:
        break;
    }
  }

  /** Learn the live sender of every command; prune dead entries. */
  private rememberMain(source: unknown): Window | null {
    if (!source || typeof source !== 'object' || !('postMessage' in source)) return null;
    const win = source as Window;
    this.pruneMains();
    this.mainTargets.add(win);
    return win;
  }

  private pruneMains(): void {
    for (const target of this.mainTargets) {
      try {
        if (target.closed) {
          this.mainTargets.delete(target);
          this.mainTabIds.delete(target);
        }
      } catch {
        this.mainTargets.delete(target);
        this.mainTabIds.delete(target);
      }
    }
  }

  /** Owner = tab id of the first live main target (adoptable when none). */
  private ownerTabId(): string | null {
    this.pruneMains();
    for (const target of this.mainTargets) {
      return this.mainTabIds.get(target) ?? null;
    }
    return null;
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.writePresence();
    this.heartbeatTimer = setInterval(() => this.writePresence(), SOFTPHONE_HEARTBEAT_INTERVAL_MS);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private writePresence(): void {
    try {
      if (typeof localStorage === 'undefined') return;
      const presence: SoftphonePresence = {
        version: SOFTPHONE_PROTOCOL_VERSION,
        ownerTabId: this.ownerTabId(),
        updatedAt: Date.now(),
      };
      localStorage.setItem(SOFTPHONE_PRESENCE_KEY, JSON.stringify(presence));
    } catch {
      /* presence is best-effort */
    }
  }

  private clearPresence(): void {
    try {
      if (typeof localStorage !== 'undefined') localStorage.removeItem(SOFTPHONE_PRESENCE_KEY);
    } catch {
      /* noop */
    }
  }

  private postToMains(msg: SoftphoneMessage): void {
    if (typeof window === 'undefined') return;
    const origin = softphoneOrigin();
    if (!origin) return;
    this.pruneMains();
    const targets = new Set<Window>(this.mainTargets);
    try {
      const opener = window.opener as Window | null;
      if (opener && !opener.closed) targets.add(opener);
    } catch {
      /* opener unreadable — skip */
    }
    for (const target of targets) {
      try {
        if (!target.closed) target.postMessage(msg, origin);
        else this.mainTargets.delete(target);
      } catch {
        this.mainTargets.delete(target);
      }
    }
  }

  private readonly onPageHide = (): void => {
    this.destroy();
  };

  private readonly onBeforeUnload = (event: BeforeUnloadEvent): void => {
    const active = this.runtime?.getActiveCalls().some((call) => !TERMINAL_CALL_STATES.has(call.state));
    if (active) {
      event.preventDefault();
    }
  };
}
