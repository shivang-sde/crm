/**
 * VOICE-02 — Generic SIP/WebRTC adapter behind the VOICE-01 contract.
 * VOICE-07 — built-in resilience supervisor: bounded backoff reconnect,
 * browser online/offline awareness, stale-generation guards, honest
 * call-loss semantics. No new runtime, no second UserAgent, no SIP.js
 * outside this module.
 *
 * Browser-only implementation detail: the ONLY module in the CRM that may
 * import SIP.js. Everything above this file speaks `VoiceProviderAdapter`.
 *
 * SIP.js is loaded lazily (`await import('sip.js')`) inside `connect()`,
 * so Next.js server compilation never evaluates browser code and no
 * browser global is touched at module scope.
 *
 * Secret discipline: the resolved SIP password lives in ONE private field
 * for the duration of the connection, is passed to SIP.js only as the
 * `authorizationPassword` option, and is wiped on disconnect/destroy.
 * It never enters events, snapshots, commands, logs, or error strings.
 */
import type { VoiceProviderAdapter } from '../adapter';
import type { VoiceEventListener, VoiceEventType } from '../events';
import { createVoiceEvent } from '../events';
import type {
  GenericSipConnectionConfig,
  VoiceCall,
  VoiceCallMetadata,
  VoiceConnectOptions,
  VoiceRegistrationSnapshot,
  VoiceRegistrationState,
} from '../types';
import { GENERIC_SIP_PROVIDER_KEY, VoiceError } from '../types';
import type { VoiceNetworkState } from '../types';
import { computeReconnectDelayMs, retryAtIso, type ReconnectPolicyOptions } from './reconnect-policy';
import {
  DISABLED_HOLD_DIAGNOSTICS,
  collectAudioElementState,
  collectPeerConnectionState,
  collectRemoteMediaState,
  collectRtpState,
  createHoldDiagnostics,
  summarizeSdp,
  summarizeSipResponse,
  type HoldDiagnostics,
  type HoldOperation,
  type HoldTrace,
  type Snapshot as HoldSnapshot,
} from '../hold-diagnostics';
import {
  collectCodecTable,
  extractInboundRaw,
  summarizeInboundAudio,
} from '../audio-diagnostics';
// Type-only: erased at build, safe for SSR. Values come from dynamic import.
import type {
  Invitation,
  Inviter,
  Registerer,
  Session,
  URI,
  UserAgent,
} from 'sip.js';

type SipLib = typeof import('sip.js');

interface ManagedCall {
  record: VoiceCall;
  session: Session;
  /** True once the dialog reached Established. */
  established: boolean;
  offStateChange: (() => void) | null;
  /** Removes the remote-track listener, if installed. */
  offTrack: (() => void) | null;
  /** Stream this call attached to the audio element (for precise cleanup). */
  attachedStream: MediaStream | null;
}

interface SipHandle {
  userAgent: UserAgent;
  registerer: Registerer | null;
}

/** Structural view of the web SessionDescriptionHandler — no deep imports. */
interface WebSdh {
  remoteMediaStream?: MediaStream;
  localMediaStream?: MediaStream;
  peerConnection?: RTCPeerConnection | undefined;
}

/**
 * Structural view of the SIP.js Session fields FIX-15 observes. None of these
 * are mutated — the adapter only reads them, and only while diagnostics run.
 */
interface SessionDiagnosticView {
  pendingReinvite?: boolean;
  sessionDescriptionHandlerModifiersReInvite?: unknown[];
  state?: { name?: string };
  sessionDescriptionHandler?: unknown;
}

const AUDIO_CONSTRAINTS = { audio: true, video: false };
const ENDED_HISTORY_LIMIT = 50;
/** Post-re-INVITE observation offsets (FIX-15 §10). Bounded, read-only. */
const HOLD_TIMELINE_OFFSETS_MS = [100, 250, 500, 1000, 2000, 5000] as const;

/** Internal marker so a superseded startup race never arms recovery. */
const SESSION_SUPERSEDED = 'SIP agent superseded during startup.';

function nowIso(): string {
  return new Date().toISOString();
}

/** Strip anything that could carry a secret out of diagnostics. */
function sanitize(message: string): string {
  return message
    .replace(/:\/\/[^/\s]+:[^/\s]+@/g, '://***:***@')
    .replace(/["']?password["']?\s*[:=]\s*["']?[^"',;}\s]+["']?/gi, '"password":"***"')
    .replace(/Authorization:[^\r\n]*/gi, 'Authorization: ***')
    .slice(0, 500);
}

function sipErrorMessage(error: unknown): string {
  if (error instanceof Error) return sanitize(error.message || error.name);
  return sanitize(String(error));
}

/** Live (not ended) audio tracks carried by a remote stream, if any. */
function liveAudioTracksOf(stream: { getAudioTracks(): MediaStreamTrack[] } | null | undefined): MediaStreamTrack[] {
  try {
    return (stream?.getAudioTracks() ?? []).filter((t) => t.readyState === 'live');
  } catch {
    return [];
  }
}

function hasLiveAudioTrack(stream: { getAudioTracks(): MediaStreamTrack[] } | null | undefined): boolean {
  return liveAudioTracksOf(stream).length > 0;
}

/**
 * Collect live remote audio tracks from receivers (deduped by id).
 * Structural typing keeps this usable without a live SIP.js session.
 */
function collectLiveRemoteAudioTracks(sdh: {
  peerConnection?: { getReceivers(): { track: MediaStreamTrack | null }[] } | null | undefined;
} | null | undefined): MediaStreamTrack[] {
  const found: MediaStreamTrack[] = [];
  const seen = new Set<string>();
  let receivers: { track: MediaStreamTrack | null }[] = [];
  try {
    receivers = sdh?.peerConnection?.getReceivers() ?? [];
  } catch {
    return found;
  }
  for (const receiver of receivers) {
    const track = receiver?.track;
    if (!track || track.kind !== 'audio' || track.readyState !== 'live') continue;
    const id = (track as MediaStreamTrack).id;
    if (typeof id === 'string' && id.length > 0) {
      if (seen.has(id)) continue;
      seen.add(id);
    }
    found.push(track as MediaStreamTrack);
  }
  return found;
}

/**
 * Harvest a SIP rejection status from a register() throw. The onReject
 * delegate usually records it first, but a rejection can win the race —
 * without this the 401/403 would misclassify as a plain registration
 * failure and (worse) re-arm the retry loop on a dead secret.
 */
function sipRejectionStatus(error: unknown): number | null {
  const seen = new Set<unknown>();
  let current: unknown = error;
  for (let depth = 0; depth < 3 && current && typeof current === 'object'; depth += 1) {
    if (seen.has(current)) break;
    seen.add(current);
    const rec = current as Record<string, unknown>;
    const direct = rec.statusCode ?? rec.status;
    if (typeof direct === 'number' && direct >= 100 && direct < 700) return direct;
    const nested = (rec.message as { statusCode?: unknown } | undefined)?.statusCode;
    if (typeof nested === 'number' && nested >= 100 && nested < 700) return nested;
    current = rec.cause ?? rec.response ?? rec.error;
  }
  return null;
}

export interface GenericSipAdapterOptions {
  /** Override backoff/clock/scheduler (deterministic tests). */
  resilience?: ReconnectPolicyOptions;
}

export class GenericSipAdapter implements VoiceProviderAdapter {
  readonly providerKey = GENERIC_SIP_PROVIDER_KEY;

  private sip: SipLib | null = null;
  private handle: SipHandle | null = null;
  private connection: GenericSipConnectionConfig | null = null;
  /** Ephemeral SIP secret — wiped on disconnect/destroy. Never logged. */
  private secret: string | null = null;
  private registration: VoiceRegistrationState = 'DISCONNECTED';
  private lastError: string | null = null;
  private readonly calls = new Map<string, ManagedCall>();
  private readonly listeners = new Map<VoiceEventType, Set<(event: never) => void>>();
  private audioElement: HTMLAudioElement | null = null;
  private callSeq = 0;
  private destroyed = false;
  private connecting: Promise<void> | null = null;

  // ---------- VOICE-07 resilience supervisor (single timer, single epoch) ----------
  /** Stale-generation guard: bumped on every teardown/destroy/(re)start. */
  private epoch = 0;
  /** True after explicit user disconnect: never auto-reconnect until connect(). */
  private manualDisconnect = false;
  /** Cancel function for the single pending reconnect timer, if any. */
  private reconnectCancel: (() => void) | null = null;
  /** Consecutive failed attempts in the current recovery run (0 = none). */
  private retryCount = 0;
  /** ISO timestamp of the next scheduled attempt (snapshot only). */
  private nextRetryAt: string | null = null;
  /** Stable reason for the last unexpected disconnect (snapshot only). */
  private lastDisconnectReason: string | null = null;
  /** True while OUR OWN unregister() is in flight (not remote expiry). */
  private unregistering = false;
  /** Last REGISTER rejection status seen (401/403 detection). */
  private lastRegisterStatus: number | null = null;
  /**
   * Monotonic register() cycle. A deferred REGISTERING-epoch failure only
   * classifies while its cycle is still current — a newer register() call
   * (manual retry, reconnect attempt) invalidates it.
   */
  private registerSeq = 0;
  /** Last network state emitted (dedupes flapping online/offline events). */
  private lastEmittedNetwork: VoiceNetworkState | null = null;
  private netListenersAttached = false;
  private readonly resilienceOpts: ReconnectPolicyOptions;
  /**
   * FIX-15 hold/resume re-INVITE diagnostics. DISABLED by default: the
   * no-op singleton is used until `setHoldDiagnosticsEnabled(true)` is called
   * by the `?diag=1` surface, so the production path allocates nothing and
   * passes no extra options to `session.invite()`.
   */
  private holdDiag: HoldDiagnostics = DISABLED_HOLD_DIAGNOSTICS;
  /** Bounded observation timers, cleared on destroy. Never mutate state. */
  private holdDiagTimers: Array<ReturnType<typeof setTimeout>> = [];

  constructor(options?: GenericSipAdapterOptions) {
    this.resilienceOpts = options?.resilience ?? {};
  }

  // ---------- lifecycle ----------

  async connect(options: VoiceConnectOptions): Promise<void> {
    this.assertUsable();
    // Explicit user intent: cancel any background recovery and start clean.
    // A later failure re-arms the supervisor from attempt 1.
    this.cancelReconnect();
    this.manualDisconnect = false;
    const connection = options.connection;
    const password = options.secret?.password;
    validateConnection(connection);
    if (!password) {
      throw new VoiceError('VOICE_NOT_CONFIGURED', 'SIP secret is required to connect.');
    }
    if (this.handle) {
      if (this.connection?.connectionId === connection.connectionId) return; // idempotent
      throw new VoiceError(
        'VOICE_PROVIDER_ERROR',
        'Already connected. Disconnect before connecting with a different configuration.',
      );
    }
    if (this.connecting) {
      await this.connecting;
      return;
    }
    this.connecting = this.startAgent(connection, password);
    try {
      await this.connecting;
    } catch (error) {
      // Unexpected failure (config/auth errors rethrow without recovery).
      this.maybeArmReconnect(error, 'initial-connect-failed');
      throw error;
    } finally {
      this.connecting = null;
    }
  }

  async disconnect(): Promise<void> {
    // Deliberate user action: never auto-reconnect afterwards.
    this.manualDisconnect = true;
    this.cancelReconnect();
    if (!this.handle) {
      if (this.registration !== 'DISCONNECTED') this.setRegistration('DISCONNECTED');
      this.emitNetwork('UNKNOWN');
      return;
    }
    await this.stopAgent();
  }

  async register(): Promise<void> {
    this.assertUsable();
    // Recovery armed but transport down (e.g. user pressed a retry affordance):
    // attempt recovery now instead of failing on the missing handle.
    if (!this.handle && this.reconnectCancel && !this.manualDisconnect && !this.destroyed) {
      await this.retryNow();
    }
    const handle = this.requireHandle();
    if (this.registration === 'REGISTERED') return; // idempotent
    this.setRegistration('REGISTERING');
    this.lastRegisterStatus = null;
    this.registerSeq += 1;
    try {
      const registerer = this.ensureRegisterer(handle);
      await registerer.register({
        requestDelegate: {
          onReject: (response) => {
            const status = response?.message?.statusCode;
            if (typeof status === 'number') this.lastRegisterStatus = status;
          },
        },
      });
      // REGISTERED arrives via registerer.stateChange; fall back if already there.
      if (this.sip && registerer.state === this.sip.RegistererState.Registered) {
        this.setRegistration('REGISTERED');
      }
    } catch (error) {
      const status = sipRejectionStatus(error);
      if (typeof status === 'number') this.lastRegisterStatus = status;
      throw this.failRegistration(error);
    }
  }

  async unregister(): Promise<void> {
    const handle = this.handle;
    if (!handle?.registerer) return; // idempotent
    this.unregistering = true;
    try {
      await handle.registerer.unregister();
    } catch (error) {
      throw new VoiceError(
        'VOICE_REGISTRATION_FAILED',
        `Unregister failed: ${sipErrorMessage(error)}`,
        true,
      );
    } finally {
      this.unregistering = false;
      this.setRegistration('DISCONNECTED');
    }
  }

  getRegistrationState(): VoiceRegistrationSnapshot {
    const snapshot: VoiceRegistrationSnapshot = {
      state: this.registration,
      registered: this.registration === 'REGISTERED',
      providerKey: this.providerKey,
      connectionId: this.connection?.connectionId ?? null,
      lastError: this.lastError,
      changedAt: nowIso(),
    };
    // Ephemeral recovery metadata only — never secrets. Shown while a
    // recovery run is active (armed timer or in-flight attempt), so the UI
    // countdown never flickers mid-attempt.
    if (this.retryCount > 0) {
      snapshot.retryCount = this.retryCount;
      snapshot.nextRetryAt = this.nextRetryAt;
      snapshot.lastDisconnectReason = this.lastDisconnectReason;
    }
    return snapshot;
  }

  // ---------- calls ----------

  async makeCall(destination: string, metadata?: VoiceCallMetadata): Promise<VoiceCall> {
    this.assertUsable();
    if (!destination?.trim()) {
      throw new VoiceError('VOICE_CALL_FAILED', 'Destination is required.');
    }
    // Honest gating while the supervisor is recovering: retryable network
    // error, not "not configured". UI disables dial on top of this.
    if (!this.handle && this.reconnectCancel && !this.manualDisconnect) {
      throw new VoiceError('VOICE_NETWORK_ERROR', 'Phone is reconnecting. Try again in a moment.', true);
    }
    const handle = this.requireHandle();
    const target = this.buildTargetUri(destination);
    const record: VoiceCall = {
      callId: this.nextCallId(),
      direction: 'OUTBOUND',
      destination,
      state: 'DIALING',
      startedAt: nowIso(),
      connectedAt: null,
      endedAt: null,
      muted: false,
      held: false,
      entityType: metadata?.entityType ?? null,
      entityId: metadata?.entityId ?? null,
    };
    let inviter: Inviter;
    try {
      const { Inviter: InviterCtor } = this.sipOrThrow();
      inviter = new InviterCtor(handle.userAgent, target, {
        sessionDescriptionHandlerOptions: { constraints: AUDIO_CONSTRAINTS },
      });
    } catch (error) {
      throw new VoiceError('VOICE_CALL_FAILED', `Dial failed: ${sipErrorMessage(error)}`);
    }
    const managed = this.trackCall(record, inviter);
    this.emit('CALL_CREATED', { ...record });
    try {
      await inviter.invite();
    } catch (error) {
      this.failCall(managed, `Invite failed: ${sipErrorMessage(error)}`);
      throw new VoiceError('VOICE_CALL_FAILED', managed.record.state === 'FAILED' ? 'Call failed.' : `Invite failed: ${sipErrorMessage(error)}`);
    }
    return { ...managed.record };
  }

  async answerCall(callId: string): Promise<VoiceCall> {
    const managed = this.requireCall(callId);
    if (managed.record.direction !== 'INBOUND') {
      throw new VoiceError('VOICE_CALL_FAILED', 'Only inbound calls can be answered.');
    }
    if (managed.record.state !== 'RINGING') return { ...managed.record }; // idempotent
    this.resumeRemoteAudio();
    try {
      await (managed.session as Invitation).accept({
        sessionDescriptionHandlerOptions: { constraints: AUDIO_CONSTRAINTS },
      });
    } catch (error) {
      throw new VoiceError('VOICE_CALL_FAILED', `Answer failed: ${sipErrorMessage(error)}`);
    }
    return { ...managed.record };
  }

  async rejectCall(callId: string): Promise<VoiceCall> {
    const managed = this.requireCall(callId);
    if (managed.record.direction !== 'INBOUND' || managed.record.state !== 'RINGING') {
      return { ...managed.record }; // idempotent — nothing to reject
    }
    try {
      await (managed.session as Invitation).reject();
    } catch (error) {
      throw new VoiceError('VOICE_CALL_FAILED', `Reject failed: ${sipErrorMessage(error)}`);
    }
    return { ...managed.record };
  }

  async hangup(callId: string): Promise<VoiceCall> {
    const managed = this.requireCall(callId);
    if (managed.record.state === 'ENDED' || managed.record.state === 'FAILED') {
      return { ...managed.record }; // idempotent
    }
    managed.record.state = 'ENDING';
    this.emit('CALL_STATE_CHANGED', { ...managed.record });
    try {
      const sip = this.sipOrThrow();
      if (managed.session instanceof sip.Inviter && !managed.established) {
        await (managed.session as Inviter).cancel();
      } else {
        await managed.session.bye();
      }
    } catch {
      // Termination is best-effort; Terminated state event finalizes the record.
      try {
        await managed.session.dispose();
      } catch {
        /* noop */
      }
    }
    return { ...managed.record };
  }

  async mute(callId: string): Promise<VoiceCall> {
    return this.setMuted(callId, true);
  }

  async unmute(callId: string): Promise<VoiceCall> {
    return this.setMuted(callId, false);
  }

  async hold(callId: string): Promise<VoiceCall> {
    const managed = this.requireCall(callId);
    if (managed.record.held) return { ...managed.record }; // idempotent
    this.requireEstablished(managed, 'hold');
    this.resumeRemoteAudio();
    // FIX-15 diagnostic: observation only. `inviteOptions` is undefined when
    // diagnostics are off, so the call below is byte-identical to before.
    const diag = this.holdDiag.enabled ? this.beginHoldDiag(managed, 'HOLD') : null;
    try {
      await managed.session.invite(
        diag
          ? { sessionDescriptionHandlerModifiers: [this.sipOrThrow().Web.holdModifier], ...diag.inviteOptions }
          : { sessionDescriptionHandlerModifiers: [this.sipOrThrow().Web.holdModifier] },
      );
    } catch (error) {
      this.finishHoldDiag(managed, diag, error);
      throw new VoiceError(
        'VOICE_OPERATION_NOT_SUPPORTED',
        `Hold not supported for this call: ${sipErrorMessage(error)}`,
      );
    }
    managed.record.held = true;
    managed.record.state = 'HELD';
    this.emit('CALL_STATE_CHANGED', { ...managed.record });
    this.finishHoldDiag(managed, diag);
    return { ...managed.record };
  }

  async resume(callId: string): Promise<VoiceCall> {
    const managed = this.requireCall(callId);
    if (!managed.record.held) return { ...managed.record }; // idempotent
    this.requireEstablished(managed, 'resume');
    this.resumeRemoteAudio();
    // FIX-15 diagnostic: `invite()` is called with NO options on purpose —
    // that is the production behavior under investigation. Only the additive
    // requestDelegate is attached, and only while diagnostics are enabled.
    const diag = this.holdDiag.enabled ? this.beginHoldDiag(managed, 'RESUME') : null;
    try {
      await managed.session.invite(diag ? { ...diag.inviteOptions } : undefined);
    } catch (error) {
      this.finishHoldDiag(managed, diag, error);
      throw new VoiceError(
        'VOICE_OPERATION_NOT_SUPPORTED',
        `Resume not supported for this call: ${sipErrorMessage(error)}`,
      );
    }
    managed.record.held = false;
    managed.record.state = 'CONNECTED';
    this.emit('CALL_STATE_CHANGED', { ...managed.record });
    this.finishHoldDiag(managed, diag);
    return { ...managed.record };
  }

  /**
   * FIX-15 §4/§12 — open a trace and capture the pre-command state.
   *
   * Returns the additive `requestDelegate` that records 1xx/2xx/non-2xx. It
   * never changes a SIP.js outcome: it only observes.
   */
  private beginHoldDiag(
    managed: ManagedCall,
    operation: HoldOperation,
  ): { trace: HoldTrace; inviteOptions: Record<string, unknown> } {
    const trace = this.holdDiag.begin(operation, managed.record.callId, managed.record.held);
    const session = managed.session as unknown as SessionDiagnosticView;
    this.holdDiag.event(trace, 'COMMAND_STARTED', {
      sessionState: session.state?.name ?? null,
      adapterCallState: managed.record.state,
      held: managed.record.held,
      pendingReinvite: session.pendingReinvite ?? null,
      reinviteModifiersInstalled: session.sessionDescriptionHandlerModifiersReInvite?.length ?? 0,
    });
    void this.holdSnapshot(managed, 'before').then((snap) => {
      this.holdDiag.attach(trace, 'before', snap);
      this.holdDiag.event(trace, 'REINVITE_STARTED', { at: snap.label });
    });
    const record = (stage: 'PROVISIONAL_RESPONSE' | 'FINAL_RESPONSE' | 'REINVITE_REJECTED' | 'REINVITE_ACCEPTED') =>
      (response: unknown): void => {
        const summary = summarizeSipResponse(response);
        trace.responses.push(summary);
        this.holdDiag.event(trace, stage, {
          statusCode: summary.statusCode,
          reasonPhrase: summary.reasonPhrase,
          cseq: summary.cseq,
          dialogTagSuffix: summary.dialogTagSuffix,
        });
        if (stage === 'PROVISIONAL_RESPONSE') return;
        // The 2xx answer is applied asynchronously by SIP.js after onAccept,
        // so read the SDP only once the peer connection has settled.
        void this.readReinviteDescriptions(managed, trace);
      };
    this.holdDiag.event(trace, 'REINVITE_REQUEST_SENT', { transactionId: trace.transactionId });
    return {
      trace,
      inviteOptions: {
        requestDelegate: {
          onTrying: record('PROVISIONAL_RESPONSE'),
          onProgress: record('PROVISIONAL_RESPONSE'),
          onAccept: record('FINAL_RESPONSE'),
          onReject: record('FINAL_RESPONSE'),
        },
      },
    };
  }

  /**
   * FIX-15 §6/§7 — read the offer the browser produced and the answer SIP.js
   * applied. Read-only: never calls setLocalDescription/setRemoteDescription.
   */
  private async readReinviteDescriptions(managed: ManagedCall, trace: HoldTrace): Promise<void> {
    const pc = this.peerConnectionOf(managed);
    if (!pc) return;
    try {
      const local = pc.localDescription;
      if (local && (!trace.outgoingSdp || trace.outgoingSdp.direction !== 'sendrecv')) {
        trace.preModifierOfferSdp = trace.preModifierOfferSdp ?? summarizeSdp(local.sdp);
        trace.outgoingSdp = summarizeSdp(local.sdp);
        this.holdDiag.event(trace, 'SDP_OUTGOING', {
          direction: trace.outgoingSdp.direction,
          payloadTypes: trace.outgoingSdp.payloadTypes,
          codecs: trace.outgoingSdp.codecs.map((c) => `${c.payloadType}/${c.name}`),
          rtcpMux: trace.outgoingSdp.rtcpMux,
          iceUfragPresent: trace.outgoingSdp.iceUfragPresent,
          icePwdPresent: trace.outgoingSdp.icePwdPresent,
          candidateCount: trace.outgoingSdp.candidateCount,
          mediaPort: trace.outgoingSdp.mediaPort,
          setup: trace.outgoingSdp.setup,
          fingerprintPresent: trace.outgoingSdp.fingerprintPresent,
          msidStreamId: trace.outgoingSdp.msidStreamId,
        });
      }
      const remote = pc.remoteDescription;
      if (remote && !trace.answerSdp) {
        trace.answerSdp = summarizeSdp(remote.sdp);
        this.holdDiag.event(trace, 'SDP_ANSWER', {
          direction: trace.answerSdp.direction,
          payloadTypes: trace.answerSdp.payloadTypes,
          codecs: trace.answerSdp.codecs.map((c) => `${c.payloadType}/${c.name}`),
          rtcpMux: trace.answerSdp.rtcpMux,
          candidateCount: trace.answerSdp.candidateCount,
          mediaPort: trace.answerSdp.mediaPort,
          setup: trace.answerSdp.setup,
          msidStreamId: trace.answerSdp.msidStreamId,
        });
      }
    } catch {
      /* observation only — never affect the call */
    }
  }

  /**
   * FIX-15 §7/§8/§9/§10 — one read-only state snapshot. Touches no track,
   * no transceiver direction, no srcObject, and never calls play()/load().
   */
  private async holdSnapshot(managed: ManagedCall, label: string): Promise<HoldSnapshot> {
    const session = managed.session as unknown as SessionDiagnosticView;
    const sdh = session.sessionDescriptionHandler as WebSdh | null | undefined;
    const pc = this.peerConnectionOf(managed);
    const sip = this.sip;
    return {
      label,
      at: Date.now(),
      sessionState: session.state?.name ?? null,
      adapterCallState: managed.record.state,
      held: managed.record.held,
      pc: collectPeerConnectionState(pc, {
        reinvitePending: session.pendingReinvite === undefined ? undefined : session.pendingReinvite,
        modifiers: session.sessionDescriptionHandlerModifiersReInvite,
        holdModifier: sip?.Web?.holdModifier,
      }),
      remote: collectRemoteMediaState(sdh ?? null),
      element: collectAudioElementState(this.audioElement),
      rtp: await collectRtpState(pc).catch(() => null),
    };
  }

  private peerConnectionOf(managed: ManagedCall): RTCPeerConnection | null {
    const sdh = (managed.session as unknown as SessionDiagnosticView)
      .sessionDescriptionHandler as WebSdh | null | undefined;
    return sdh?.peerConnection ?? null;
  }

  /**
   * FIX-15 §4/§10 — finish the trace and schedule the bounded post-command
   * observation timeline (+100ms … +5s). Never mutates call state.
   */
  private finishHoldDiag(managed: ManagedCall, diag: { trace: HoldTrace } | null, error?: unknown): void {
    if (!diag) return;
    const { trace } = diag;
    void this.holdSnapshot(managed, 'after').then((snap) => {
      this.holdDiag.attach(trace, 'after', snap);
      this.holdDiag.timeline(trace, snap);
      trace.holdStateAfter = managed.record.held;
      this.holdDiag.event(trace, error === undefined ? 'REINVITE_ACCEPTED' : 'REINVITE_FAILED', {
        held: managed.record.held,
        adapterCallState: managed.record.state,
      });
      if (error !== undefined) this.holdDiag.event(trace, 'REINVITE_REJECTED', { at: snap.label });
      this.holdDiag.event(trace, 'COMMAND_COMPLETED', { at: snap.label });
      this.holdDiag.complete(trace, error);
      this.scheduleHoldTimeline(managed, trace);
    });
  }

  private scheduleHoldTimeline(managed: ManagedCall, trace: HoldTrace): void {
    for (const offset of HOLD_TIMELINE_OFFSETS_MS) {
      const timer = setTimeout(() => {
        if (this.destroyed || !this.holdDiag.enabled) return;
        if (!this.calls.has(managed.record.callId)) return;
        void this.holdSnapshot(managed, `+${offset}ms`).then((snap) => {
          this.holdDiag.timeline(trace, snap);
          this.holdDiag.event(trace, 'OBSERVATION', { label: snap.label, rtp: snap.rtp?.present ?? false });
          // Re-classify as the timeline fills in (RTP deltas need it).
          this.holdDiag.complete(trace);
        });
      }, offset);
      // Timers self-expire within 5s; clearHoldDiagTimers() drops the rest on
      // disable/destroy. Nothing is pruned here so a fast HOLD→RESUME keeps
      // the HOLD tail observations.
      this.holdDiagTimers.push(timer);
    }
  }

  private clearHoldDiagTimers(): void {
    for (const timer of this.holdDiagTimers) clearTimeout(timer);
    this.holdDiagTimers = [];
  }

  async transfer(callId: string, destination: string): Promise<VoiceCall> {
    const managed = this.requireCall(callId);
    this.requireEstablished(managed, 'transfer');
    const target = this.buildTargetUri(destination);
    try {
      await managed.session.refer(target);
    } catch (error) {
      throw new VoiceError(
        'VOICE_OPERATION_NOT_SUPPORTED',
        `Transfer not supported for this call: ${sipErrorMessage(error)}`,
      );
    }
    return { ...managed.record };
  }

  getCallState(callId: string): VoiceCall | null {
    const managed = this.calls.get(callId);
    return managed ? { ...managed.record } : null;
  }

  getActiveCalls(): VoiceCall[] {
    return [...this.calls.values()]
      .filter((m) => m.record.state !== 'ENDED' && m.record.state !== 'FAILED')
      .map((m) => ({ ...m.record }));
  }

  on<T extends VoiceEventType>(type: T, listener: VoiceEventListener<T>): () => void {
    let set = this.listeners.get(type);
    if (!set) {
      set = new Set();
      this.listeners.set(type, set);
    }
    const fn = listener as (event: never) => void;
    set.add(fn);
    return () => {
      set.delete(fn);
    };
  }

  /**
   * UI override for the remote-audio sink. When unset, the adapter owns a
   * single lazily-created HTMLAudioElement. Browser-only — call from UI code.
   * Re-attaches live remote audio when the element is (re)assigned after
   * tracks already arrived (covers both track/element orderings).
   */
  setAudioElement(element: HTMLAudioElement | null): void {
    this.audioElement = element;
    if (!element || this.destroyed) return;
    for (const managed of this.calls.values()) {
      if (managed.record.state !== 'CONNECTED' && managed.record.state !== 'HELD') continue;
      this.attachRemoteAudio(managed);
    }
  }

  /**
   * FIX-15: enable the hold/resume re-INVITE diagnostic trace.
   *
   * Called only from the `?diag=1` surface. Enabling it changes nothing about
   * signaling: the trace is observation-only and the hold/resume paths keep
   * their existing `session.invite()` arguments, gaining only an additive
   * `requestDelegate` so 1xx/2xx/non-2xx become visible.
   */
  setHoldDiagnosticsEnabled(enabled: boolean): void {
    if (enabled === this.holdDiag.enabled) return;
    this.clearHoldDiagTimers();
    this.holdDiag = enabled ? createHoldDiagnostics() : DISABLED_HOLD_DIAGNOSTICS;
  }

  /** FIX-15: bounded, redacted hold/resume traces for the ?diag=1 panel. */
  getHoldDiagnostics(): HoldTrace[] {
    return this.holdDiag.enabled ? this.holdDiag.list() : [];
  }

  destroy(): void {
    if (this.destroyed) return; // idempotent
    this.destroyed = true;
    this.epoch += 1;
    this.cancelReconnect();
    this.clearHoldDiagTimers();
    this.detachNetListeners();
    for (const managed of this.calls.values()) {
      this.detachSession(managed);
    }
    this.calls.clear();
    this.listeners.clear();
    this.releaseAudio();
    // Fire-and-forget: destroy() is synchronous by contract.
    void this.stopAgent().catch(() => undefined);
    this.sip = null;
  }

  // ---------- internals ----------

  private async startAgent(connection: GenericSipConnectionConfig, password: string): Promise<void> {
    this.assertBrowser();
    const myEpoch = this.bumpEpoch();
    this.setRegistration('CONNECTING');
    // Retain up front (non-secret config + in-memory secret) so an
    // unexpected failure can arm background recovery even when the first
    // attempt never completed. Wiped on manual disconnect/destroy/auth
    // failure — never persisted, never logged.
    this.connection = connection;
    this.secret = password;
    const sip = await import('sip.js');
    if (this.destroyed || this.handle || myEpoch !== this.epoch) {
      return; // destroyed, raced, or superseded while loading
    }
    this.sip = sip;
    this.attachNetListeners();
    let userAgent: UserAgent;
    try {
      const uri = sip.UserAgent.makeURI(`sip:${connection.username}@${connection.sipDomain}`);
      if (!uri) throw new Error('Unable to build SIP address-of-record.');
      userAgent = new sip.UserAgent({
        uri,
        transportOptions: { server: connection.endpoint },
        authorizationUsername: connection.username,
        authorizationPassword: password,
        sessionDescriptionHandlerFactoryOptions: {
          peerConnectionConfiguration: toPeerConnectionConfig(connection),
        },
        delegate: {
          onInvite: (invitation) => {
            if (myEpoch === this.epoch) this.onIncomingInvite(invitation);
          },
          // Fires on leaving Connected — including our own stop(). Guarded
          // by epoch + handle + manual flag; never loops back synchronously.
          onDisconnect: () => this.onTransportLost(myEpoch),
        },
      });
      await userAgent.start();
    } catch (error) {
      if (myEpoch !== this.epoch || this.destroyed) {
        throw new VoiceError('VOICE_PROVIDER_ERROR', SESSION_SUPERSEDED);
      }
      this.sip = null;
      throw this.failConnection(error);
    }
    if (myEpoch !== this.epoch || this.destroyed || this.handle) {
      try {
        await userAgent.stop();
      } catch {
        /* noop — superseded agent torn down best-effort */
      }
      return;
    }
    this.handle = { userAgent, registerer: null };
    // Auto-register: connect() brings the endpoint to REGISTERED.
    try {
      await this.register();
    } catch (error) {
      if (myEpoch !== this.epoch) throw error;
      // Loss-style teardown (keeps credentials for supervisor retry);
      // callers decide between RECONNECTING (armed) and FAILED (declined).
      await this.teardownTransportLoss('SIP registration failed.').catch(() => undefined);
      throw error;
    }
  }

  private async stopAgent(): Promise<void> {
    this.bumpEpoch();
    this.cancelReconnect();
    // Clear any in-flight joiners: their epoch is stale, so they abort
    // instead of resurrecting a torn-down session.
    this.connecting = null;
    const handle = this.handle;
    this.handle = null;
    this.connection = null;
    this.secret = null; // wipe the instant the connection goes down
    for (const managed of this.calls.values()) {
      this.detachSession(managed);
      if (managed.record.state !== 'ENDED' && managed.record.state !== 'FAILED') {
        managed.record.state = 'ENDED';
        managed.record.endedAt = nowIso();
        this.emit('CALL_ENDED', { ...managed.record });
      }
    }
    this.pruneEndedCalls();
    await this.disposeSipObjects(handle);
    this.releaseAudio();
    this.setRegistration('DISCONNECTED');
    this.emitNetwork('UNKNOWN');
  }

  /**
   * VOICE-07 — tear down after UNEXPECTED transport loss. Unlike manual
   * disconnect: keeps connection config + secret in memory for the
   * supervisor's reconnect attempts, marks interrupted calls FAILED
   * (never ENDED — they did not end normally), and leaves no SIP objects.
   */
  private async teardownTransportLoss(reason: string): Promise<void> {
    this.bumpEpoch();
    this.connecting = null;
    const handle = this.handle;
    this.handle = null;
    for (const managed of this.calls.values()) {
      this.detachSession(managed);
      if (managed.record.state === 'ENDED' || managed.record.state === 'FAILED') continue;
      managed.record.state = 'FAILED';
      managed.record.endedAt = nowIso();
      this.emit('ERROR', {
        code: 'VOICE_CALL_FAILED',
        message: `Call lost: ${reason}`,
        callId: managed.record.callId,
      });
      this.emit('CALL_ENDED', { ...managed.record });
    }
    this.pruneEndedCalls();
    await this.disposeSipObjects(handle);
    this.releaseAudio();
  }

  private async disposeSipObjects(handle: SipHandle | null): Promise<void> {
    if (handle?.registerer) {
      try {
        await handle.registerer.unregister().catch(() => undefined);
      } finally {
        try {
          await handle.registerer.dispose();
        } catch {
          /* noop */
        }
      }
      handle.registerer = null;
    }
    if (handle) {
      try {
        await handle.userAgent.stop();
      } catch {
        /* noop — stop is best-effort during teardown */
      }
    }
  }

  /**
   * Classify a terminal state reached while REGISTERING. sip.js notifies
   * stateChange BEFORE requestDelegate.onReject on the same synchronous tick
   * (see Registerer.register rejection path), so the rejection status is not
   * visible yet — defer one microtask so onReject records it first. The
   * emission stays single and correctly classified: generation, cycle, state,
   * manual, and destroyed guards drop stale/superseded failures. No timers,
   * no second pathway — failRegistration keeps central classification.
   */
  private failRegisteringEpoch(myEpoch: number, seq: number, message: string): void {
    queueMicrotask(() => {
      if (myEpoch !== this.epoch || this.destroyed || this.manualDisconnect) return;
      if (seq !== this.registerSeq) return;
      if (this.registration !== 'REGISTERING') return;
      this.failRegistration(new Error(message));
    });
  }

  private ensureRegisterer(handle: SipHandle): Registerer {
    if (!handle.registerer) {
      const sip = this.sipOrThrow();
      const registerer = new sip.Registerer(handle.userAgent);
      const myEpoch = this.epoch;
      registerer.stateChange.addListener((state) => {
        if (myEpoch !== this.epoch) return; // stale generation: never resurrect state
        if (state === sip.RegistererState.Registered) {
          this.lastError = null;
          this.lastRegisterStatus = null;
          this.setRegistration('REGISTERED');
        } else if (state === sip.RegistererState.Unregistered) {
          if (this.registration === 'REGISTERING') {
            this.failRegisteringEpoch(myEpoch, this.registerSeq, 'Registration rejected by the SIP server.');
          } else if (!this.unregistering && this.registration === 'REGISTERED') {
            // Remote expiry while healthy (not our own unregister): recover.
            this.beginRecovery('registration-lost', 'SIP registration lost.', 'VOICE_REGISTRATION_FAILED');
          }
        } else if (state === sip.RegistererState.Terminated) {
          if (this.registration === 'REGISTERING') {
            this.failRegisteringEpoch(myEpoch, this.registerSeq, 'Registration terminated.');
          } else if (!this.unregistering && this.registration === 'REGISTERED') {
            this.beginRecovery('registration-lost', 'SIP registration terminated.', 'VOICE_REGISTRATION_FAILED');
          }
        }
      });
      handle.registerer = registerer;
    }
    return handle.registerer;
  }

  private onTransportLost(myEpoch: number): void {
    // Guarded three ways: stale epoch, manual teardown (handle already
    // nulled before userAgent.stop()), and explicit manual intent.
    if (myEpoch !== this.epoch || this.destroyed || this.manualDisconnect) return;
    if (!this.handle) return;
    this.beginRecovery('transport-lost', 'SIP transport disconnected.', 'VOICE_NETWORK_ERROR');
  }

  /**
   * Shared entry for unexpected health loss (transport drop or remote
   * registration expiry). Marks the endpoint RECONNECTING, fails
   * interrupted calls honestly, and arms exactly one recovery timer.
   * Callers must have validated generation/manual/destroyed already.
   */
  private beginRecovery(
    reason: string,
    message: string,
    code: 'VOICE_NETWORK_ERROR' | 'VOICE_REGISTRATION_FAILED',
  ): void {
    this.lastError = message;
    this.lastDisconnectReason = reason;
    this.setRegistration('RECONNECTING');
    this.emitNetwork(this.isBrowserOffline() ? 'OFFLINE' : 'RECONNECTING');
    this.emit('ERROR', { code, message: `${message} Reconnecting.` });
    void this.teardownTransportLoss(message).catch(() => undefined);
    this.armReconnect();
  }

  // ---------- VOICE-07 resilience supervisor ----------

  private bumpEpoch(): number {
    this.epoch += 1;
    return this.epoch;
  }

  private resilienceNow(): number {
    return this.resilienceOpts.now?.() ?? Date.now();
  }

  private isBrowserOffline(): boolean {
    try {
      if (typeof navigator !== 'undefined' && typeof navigator.onLine === 'boolean') {
        return !navigator.onLine;
      }
    } catch {
      /* noop — unknown means treat as online */
    }
    return false;
  }

  private attachNetListeners(): void {
    if (this.netListenersAttached || typeof window === 'undefined') return;
    this.netListenersAttached = true;
    window.addEventListener('online', this.onOnline);
    window.addEventListener('offline', this.onOffline);
  }

  private detachNetListeners(): void {
    if (!this.netListenersAttached || typeof window === 'undefined') return;
    this.netListenersAttached = false;
    window.removeEventListener('online', this.onOnline);
    window.removeEventListener('offline', this.onOffline);
  }

  private readonly onOnline = (): void => {
    if (this.destroyed || this.manualDisconnect) return;
    // Transport survived the flap (no disconnect fired): restore ONLINE so
    // a stale OFFLINE badge cannot stick.
    if (this.handle && !this.reconnectCancel) {
      this.emitNetwork('ONLINE');
      return;
    }
    // Exactly one controlled recovery attempt; never a second runtime —
    // retryNow joins an in-flight attempt instead of starting another.
    if (this.reconnectCancel) {
      void this.retryNow().catch(() => undefined);
    }
  };

  private readonly onOffline = (): void => {
    if (this.destroyed) return;
    // Only meaningful while a session exists or is being recovered;
    // otherwise this would spam events on an idle phone.
    if (!this.handle && !this.reconnectCancel) return;
    this.emitNetwork('OFFLINE');
    this.emit('ERROR', { code: 'VOICE_NETWORK_OFFLINE', message: 'Browser is offline. Reconnecting when online.' });
  };

  /** Emit NETWORK_CHANGED with dedup so flapping online/offline can't storm. */
  private emitNetwork(state: VoiceNetworkState): void {
    if (this.lastEmittedNetwork === state) return;
    this.lastEmittedNetwork = state;
    this.emit('NETWORK_CHANGED', { state, changedAt: nowIso() });
  }

  /**
   * Decide whether a failure should arm background recovery. Never arms for
   * missing config, auth failure, superseded races, manual or destroyed
   * adapters. Auth failures wipe the retained secret so the next manual
   * connect re-resolves fresh credentials.
   */
  private maybeArmReconnect(error: unknown, reason?: string): void {
    if (this.destroyed || this.manualDisconnect || !this.connection || !this.secret) return;
    if (error instanceof VoiceError) {
      if (error.code === 'VOICE_NOT_CONFIGURED') return;
      if (error.code === 'VOICE_AUTH_REQUIRED') {
        this.secret = null;
        return;
      }
      if (error.code === 'VOICE_PROVIDER_ERROR' && error.message === SESSION_SUPERSEDED) return;
    }
    if (this.lastRegisterStatus === 401 || this.lastRegisterStatus === 403) {
      this.secret = null;
      return;
    }
    // Preserve the original loss reason across attempts of the same run;
    // cancelReconnect() clears it when a run ends.
    if (reason && !this.lastDisconnectReason) this.lastDisconnectReason = reason;
    this.armReconnect();
  }

  /** Schedule the next attempt (attempt numbers start at 1). Exactly one timer. */
  private armReconnect(): void {
    if (this.destroyed || this.manualDisconnect) return;
    if (!this.connection || !this.secret) return;
    this.cancelReconnectTimer();
    const attempt = this.retryCount + 1;
    const delay = computeReconnectDelayMs(attempt, this.resilienceOpts);
    const epochAtArm = this.epoch;
    this.retryCount = attempt;
    this.nextRetryAt = retryAtIso(this.resilienceNow(), delay);
    this.setRegistration('RECONNECTING');
    this.emitNetwork(this.isBrowserOffline() ? 'OFFLINE' : 'RECONNECTING');
    const schedule = this.resilienceOpts.schedule
      ?? ((ms: number, cb: () => void) => {
        const timer = setTimeout(cb, ms);
        return () => clearTimeout(timer);
      });
    this.reconnectCancel = schedule(delay, () => {
      this.reconnectCancel = null;
      void this.runReconnectAttempt(epochAtArm).catch(() => undefined);
    });
  }

  /** Fire the pending attempt now (online event / manual retry affordance). */
  private async retryNow(): Promise<void> {
    if (this.destroyed || this.manualDisconnect) return;
    if (this.connecting) {
      await this.connecting;
      return;
    }
    this.cancelReconnectTimer();
    await this.runReconnectAttempt(this.epoch);
  }

  private async runReconnectAttempt(myEpoch: number): Promise<void> {
    if (myEpoch !== this.epoch || this.destroyed || this.manualDisconnect) return;
    const connection = this.connection;
    const password = this.secret;
    if (!connection || !password) return;
    // Offline: suppress the pointless attempt, keep the single timer alive
    // at the same backoff step until connectivity returns.
    if (this.isBrowserOffline()) {
      this.emitNetwork('OFFLINE');
      this.armReconnectAtSameStep();
      return;
    }
    const attempt = this.startAgent(connection, password);
    this.connecting = attempt;
    try {
      await attempt;
    } catch (error) {
      // startAgent bumps the epoch for its own attempt, so comparing against
      // the arming epoch here would mistake our own bump for a newer
      // generation and break the loop after the first retry. Newer
      // generations are covered: destroy/manual checked directly, superseded
      // races via maybeArmReconnect, concurrent connect() joins in-flight.
      if (this.destroyed || this.manualDisconnect) throw error;
      this.maybeArmReconnect(error, 'reconnect-attempt-failed');
      throw error;
    } finally {
      if (this.connecting === attempt) this.connecting = null;
    }
  }

  /** Re-schedule the current attempt (used when offline suppresses a firing). */
  private armReconnectAtSameStep(): void {
    if (this.destroyed || this.manualDisconnect) return;
    if (!this.connection || !this.secret) return;
    this.cancelReconnectTimer();
    const delay = computeReconnectDelayMs(Math.max(1, this.retryCount), this.resilienceOpts);
    const epochAtArm = this.epoch;
    this.nextRetryAt = retryAtIso(this.resilienceNow(), delay);
    this.setRegistration('RECONNECTING');
    this.emitNetwork('OFFLINE');
    const schedule = this.resilienceOpts.schedule
      ?? ((ms: number, cb: () => void) => {
        const timer = setTimeout(cb, ms);
        return () => clearTimeout(timer);
      });
    this.reconnectCancel = schedule(delay, () => {
      this.reconnectCancel = null;
      void this.runReconnectAttempt(epochAtArm).catch(() => undefined);
    });
  }

  /** Cancel the pending timer and reset recovery bookkeeping. */
  private cancelReconnect(): void {
    this.cancelReconnectTimer();
    this.retryCount = 0;
    this.nextRetryAt = null;
    this.lastDisconnectReason = null;
  }

  private cancelReconnectTimer(): void {
    if (this.reconnectCancel) {
      try {
        this.reconnectCancel();
      } catch {
        /* noop */
      }
      this.reconnectCancel = null;
    }
    this.nextRetryAt = null;
  }

  private onIncomingInvite(invitation: Invitation): void {
    if (this.destroyed) return;
    const caller = invitation.remoteIdentity?.uri?.user ?? invitation.remoteIdentity?.uri?.toString() ?? 'unknown';
    const record: VoiceCall = {
      callId: this.nextCallId(),
      direction: 'INBOUND',
      destination: sanitize(caller),
      state: 'RINGING',
      startedAt: nowIso(),
      connectedAt: null,
      endedAt: null,
      muted: false,
      held: false,
      entityType: null,
      entityId: null,
    };
    this.trackCall(record, invitation);
    this.emit('CALL_CREATED', { ...record });
    this.emit('CALL_STATE_CHANGED', { ...record });
  }

  private trackCall(record: VoiceCall, session: Session): ManagedCall {
    const sip = this.sipOrThrow();
    const managed: ManagedCall = { record, session, established: false, offStateChange: null, offTrack: null, attachedStream: null };
    const off = (session.stateChange.addListener((state) => {
      if (state === sip.SessionState.Established) {
        managed.established = true;
        managed.record.state = managed.record.held ? 'HELD' : 'CONNECTED';
        managed.record.connectedAt = managed.record.connectedAt ?? nowIso();
        this.attachRemoteAudio(managed);
        this.emit('CALL_STATE_CHANGED', { ...managed.record });
      } else if (state === sip.SessionState.Terminated) {
        this.finishCall(managed);
      } else if (state === sip.SessionState.Establishing && managed.record.state === 'DIALING') {
        this.emit('CALL_STATE_CHANGED', { ...managed.record });
      }
    }) as unknown as () => void | undefined);
    managed.offStateChange = typeof off === 'function' ? (off as () => void) : null;
    this.calls.set(record.callId, managed);
    return managed;
  }

  private finishCall(managed: ManagedCall): void {
    const wasEstablished = managed.established;
    managed.record.endedAt = nowIso();
    managed.record.state = wasEstablished ? 'ENDED' : 'FAILED';
    this.releaseCallMedia(managed);
    if (!wasEstablished) {
      this.emit('ERROR', {
        code: 'VOICE_CALL_FAILED',
        message: 'Call failed before connecting.',
        callId: managed.record.callId,
      });
    }
    this.emit('CALL_ENDED', { ...managed.record });
    this.pruneEndedCalls();
  }

  private failCall(managed: ManagedCall, message: string): void {
    managed.record.state = 'FAILED';
    managed.record.endedAt = nowIso();
    this.releaseCallMedia(managed);
    this.emit('ERROR', { code: 'VOICE_CALL_FAILED', message: sanitize(message), callId: managed.record.callId });
    this.emit('CALL_ENDED', { ...managed.record });
  }

  private attachRemoteAudio(managed: ManagedCall): void {
    if (typeof window === 'undefined') return;
    const element = this.audioElement ?? (this.audioElement = new Audio());
    this.playRemoteStream(managed, element);
    this.watchRemoteTracks(managed);
  }

  /**
   * The SIP.js remote MediaStream object is created empty and filled when
   * tracks arrive — possibly AFTER Established. Prefer that live object so
   * late tracks flow automatically; otherwise build from live receivers.
   */
  private remoteStreamFor(managed: ManagedCall): MediaStream | null {
    const sdh = managed.session.sessionDescriptionHandler as unknown as WebSdh | null;
    if (sdh?.remoteMediaStream) return sdh.remoteMediaStream;
    const tracks = collectLiveRemoteAudioTracks(sdh);
    return tracks.length > 0 ? new MediaStream(tracks) : null;
  }

  /** Attach the live remote stream and (re)start playback when tracks exist. */
  private playRemoteStream(managed: ManagedCall, element: HTMLAudioElement): void {
    const stream = this.remoteStreamFor(managed);
    if (!stream) return; // no live audio yet — the track listener retries
    try {
      if (element.srcObject !== stream) element.srcObject = stream;
      managed.attachedStream = stream;
      if (!hasLiveAudioTrack(stream) || !element.paused) return;
      const played = element.play();
      if (played && typeof played.catch === 'function') {
        played.catch(() => {
          this.emit('ERROR', {
            code: 'VOICE_CALL_FAILED',
            message: 'Remote audio is blocked by browser autoplay policy. Resume playback from a user gesture.',
            callId: managed.record.callId,
          });
        });
      }
    } catch (error) {
      this.emit('ERROR', {
        code: 'VOICE_CALL_FAILED',
        message: `Remote audio unavailable: ${sipErrorMessage(error)}`,
        callId: managed.record.callId,
      });
    }
  }

  /**
   * Late-track safety net: tracks arriving after Established (or after a
   * rejected play()) re-run attach. Additive listener — never overwrites
   * the SIP.js `pc.ontrack` assignment. Removed with the call.
   */
  private watchRemoteTracks(managed: ManagedCall): void {
    if (managed.offTrack) return;
    const sdh = managed.session.sessionDescriptionHandler as unknown as WebSdh | null;
    const pc = sdh?.peerConnection;
    if (!pc || typeof pc.addEventListener !== 'function') return;
    const onTrack = (event: Event): void => {
      const track = (event as RTCTrackEvent).track;
      if (!track || track.kind !== 'audio') return;
      if (this.destroyed) return;
      if (managed.record.state !== 'CONNECTED' && managed.record.state !== 'HELD') return;
      const element = this.audioElement ?? (this.audioElement = new Audio());
      this.playRemoteStream(managed, element);
    };
    try {
      pc.addEventListener('track', onTrack);
    } catch {
      return;
    }
    managed.offTrack = () => {
      try {
        pc.removeEventListener('track', onTrack);
      } catch {
        /* noop */
      }
    };
  }

  /**
   * Best-effort playback resume on fresh user gestures (answer/mute/hold/
   * resume arrive with transient activation; an earlier play() may have been
   * rejected after ring time expired it). Silent: the initial attach already
   * surfaced a blocking error if playback truly failed.
   */
  private resumeRemoteAudio(): void {
    if (typeof window === 'undefined' || this.destroyed) return;
    const element = this.audioElement;
    if (!element || !element.paused) return;
    for (const managed of this.calls.values()) {
      if (managed.record.state !== 'CONNECTED' && managed.record.state !== 'HELD') continue;
      const stream = this.remoteStreamFor(managed);
      if (!stream || !hasLiveAudioTrack(stream)) continue;
      try {
        if (element.srcObject !== stream) element.srcObject = stream;
        managed.attachedStream = stream;
        const played = element.play();
        if (played && typeof played.catch === 'function') played.catch(() => undefined);
      } catch {
        /* noop — retry on the next gesture */
      }
      return;
    }
  }

  private setMuted(callId: string, muted: boolean): Promise<VoiceCall> {
    const managed = this.requireCall(callId);
    if (managed.record.muted === muted) return Promise.resolve({ ...managed.record }); // idempotent
    this.resumeRemoteAudio();    this.requireEstablished(managed, muted ? 'mute' : 'unmute');
    setLocalAudioEnabled(managed.session, !muted);
    managed.record.muted = muted;
    this.emit('CALL_STATE_CHANGED', { ...managed.record });
    return Promise.resolve({ ...managed.record });
  }

  private buildTargetUri(destination: string): URI {
    const sip = this.sipOrThrow();
    const trimmed = destination.trim();
    if (!trimmed) throw new VoiceError('VOICE_CALL_FAILED', 'Destination is required.');
    const withScheme = trimmed.includes(':') ? trimmed : `sip:${trimmed}`;
    let uri = sip.UserAgent.makeURI(withScheme);
    if (!uri && this.connection) {
      // Extension / phone number for the configured SIP server: user@domain.
      uri = sip.UserAgent.makeURI(`sip:${trimmed}@${this.connection.sipDomain}`);
    }
    if (!uri) throw new VoiceError('VOICE_CALL_FAILED', 'Destination is not a valid SIP address.');
    return uri;
  }

  private setRegistration(state: VoiceRegistrationState): void {
    this.registration = state;
    if (state === 'REGISTERED') {
      // Recovery boundary: actual SIP confirmation resets everything.
      // Harmless on manual flows (counters are already zero there).
      this.cancelReconnectTimer();
      this.retryCount = 0;
      this.nextRetryAt = null;
      this.lastDisconnectReason = null;
      this.emitNetwork('ONLINE');
    }
    this.emit('REGISTRATION_CHANGED', this.getRegistrationState());
  }

  private failConnection(error: unknown): VoiceError {
    const message = sipErrorMessage(error);
    this.lastError = message;
    this.setRegistration('FAILED');
    this.emit('ERROR', { code: 'VOICE_CONNECTION_FAILED', message });
    return new VoiceError('VOICE_CONNECTION_FAILED', message, true);
  }

  private failRegistration(error: unknown): VoiceError {
    // 401/403 means the credentials themselves are rejected: surface auth
    // distinctly so callers stop retrying, never loop on a dead secret.
    if (this.lastRegisterStatus === 401 || this.lastRegisterStatus === 403) {
      const message = sipErrorMessage(error);
      this.lastError = message;
      this.setRegistration('FAILED');
      this.emit('ERROR', { code: 'VOICE_AUTH_REQUIRED', message });
      return new VoiceError('VOICE_AUTH_REQUIRED', message);
    }
    // VOICE-01 exposes a single registration code; auth detail stays in the message.
    const message = sipErrorMessage(error);
    this.lastError = message;
    this.setRegistration('FAILED');
    this.emit('ERROR', { code: 'VOICE_REGISTRATION_FAILED', message });
    return new VoiceError('VOICE_REGISTRATION_FAILED', message, true);
  }

  private requireHandle(): SipHandle {
    if (!this.handle || !this.sip) {
      throw new VoiceError('VOICE_NOT_CONFIGURED', 'Not connected. Call connect() first.');
    }
    return this.handle;
  }

  private requireCall(callId: string): ManagedCall {
    const managed = this.calls.get(callId);
    if (!managed) throw new VoiceError('VOICE_CALL_NOT_FOUND', 'Call not found.');
    return managed;
  }

  private requireEstablished(managed: ManagedCall, operation: string): void {
    if (!managed.established) {
      throw new VoiceError('VOICE_CALL_FAILED', `Cannot ${operation} a call that is not connected.`);
    }
  }

  private sipOrThrow(): SipLib {
    if (!this.sip) throw new VoiceError('VOICE_NOT_CONFIGURED', 'Not connected. Call connect() first.');
    return this.sip;
  }

  private assertUsable(): void {
    if (this.destroyed) throw new VoiceError('VOICE_PROVIDER_ERROR', 'Adapter has been destroyed.');
  }

  private assertBrowser(): void {
    if (typeof window === 'undefined') {
      throw new VoiceError('VOICE_PROVIDER_ERROR', 'SIP adapter requires a browser environment.');
    }
  }

  private nextCallId(): string {
    this.callSeq += 1;
    const random = typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID().slice(0, 8)
      : Math.random().toString(36).slice(2, 10);
    return `sip-${Date.now().toString(36)}-${this.callSeq}-${random}`;
  }

  private detachSession(managed: ManagedCall): void {
    try {
      managed.offStateChange?.();
    } catch {
      /* noop */
    }
    managed.offStateChange = null;
    this.releaseCallMedia(managed);
    try {
      void managed.session.dispose();
    } catch {
      /* noop */
    }
  }

  /**
   * FIX-9 read-only remote-media diagnostics for the popup-local audio
   * panel. No state change, no secrets, no logging. Returns null when no
   * established call exists. FIX-11: also carries the allowlisted raw
   * inbound-rtp fields plus the codec table for forensics.
   */
  async getRemoteAudioStats(): Promise<{
    callId: string;
    connectionState: string | null;
    iceConnectionState: string | null;
    inboundAudio: {
      packetsReceived: number;
      bytesReceived: number;
      packetsLost: number;
      jitter: number | null;
      jitterBufferDelay: number | null;
      concealedSamples: number;
      totalSamplesReceived: number;
      framesDecoded: number;
      codec: string | null;
    } | null;
    inboundRaw: Record<string, number | string | boolean | null> | null;
    codecs: Record<string, { mimeType: string; clockRate: string; channels: string; payloadType: string; sdpFmtpLine: string }>;
    receiverLive: boolean;
    receiverTrackId: string | null;
  } | null> {
    if (typeof window === 'undefined' || this.destroyed) return null;
    for (const managed of this.calls.values()) {
      if (managed.record.state !== 'CONNECTED' && managed.record.state !== 'HELD') continue;
      const sdh = managed.session.sessionDescriptionHandler as unknown as WebSdh | null;
      const pc = sdh?.peerConnection ?? null;
      const empty = {
        callId: managed.record.callId,
        connectionState: null as string | null,
        iceConnectionState: null as string | null,
        inboundAudio: null as null | {
          packetsReceived: number; bytesReceived: number; packetsLost: number; jitter: number | null;
          jitterBufferDelay: number | null; concealedSamples: number; totalSamplesReceived: number;
          framesDecoded: number; codec: string | null;
        },
        inboundRaw: null as null | Record<string, number | string | boolean | null>,
        codecs: {} as Record<string, { mimeType: string; clockRate: string; channels: string; payloadType: string; sdpFmtpLine: string }>,
        receiverLive: false,
        receiverTrackId: null as string | null,
      };
      if (!pc) return empty;
      empty.connectionState = pc.connectionState ?? null;
      empty.iceConnectionState = pc.iceConnectionState ?? null;
      try {
        const audioReceiver = pc.getReceivers().find((receiver) => receiver.track?.kind === 'audio');
        if (audioReceiver?.track) {
          empty.receiverTrackId = audioReceiver.track.id ?? null;
          empty.receiverLive = audioReceiver.track.readyState === 'live';
        }
      } catch {
        empty.receiverLive = false;
      }
      try {
        const stats = await pc.getStats();
        const entries: unknown[] = [];
        stats.forEach((report: unknown) => {
          entries.push(report);
        });
        const codecs = collectCodecTable(entries);
        for (const [id, entry] of Object.entries(codecs)) empty.codecs[id] = entry;
        empty.inboundAudio = summarizeInboundAudio(entries, codecs);
        empty.inboundRaw = extractInboundRaw(entries);
      } catch {
        /* stats unavailable — still report states above */
      }
      return empty;
    }
    return null;
  }

  private releaseCallMedia(managed: ManagedCall): void {
    try {
      managed.offTrack?.();
    } catch {
      /* noop */
    }
    managed.offTrack = null;
    const sdh = managed.session.sessionDescriptionHandler as unknown as WebSdh | null;
    try {
      sdh?.peerConnection?.getSenders().forEach((sender) => {
        try {
          sender.track?.stop();
        } catch {
          /* noop */
        }
      });
      sdh?.localMediaStream?.getTracks().forEach((track) => {
        try {
          track.stop();
        } catch {
          /* noop */
        }
      });
    } catch {
      /* noop — media release is best-effort */
    }
    try {
      const attached = managed.attachedStream;
      managed.attachedStream = null;
      if (this.audioElement && attached && this.audioElement.srcObject === attached) {
        this.audioElement.srcObject = null;
      } else if (this.audioElement && this.audioElement.srcObject === (sdh?.remoteMediaStream ?? null)) {
        this.audioElement.srcObject = null;
      }
    } catch {
      /* noop */
    }
  }

  private releaseAudio(): void {
    try {
      if (this.audioElement) this.audioElement.srcObject = null;
    } catch {
      /* noop */
    }
    this.audioElement = null;
  }

  private pruneEndedCalls(): void {
    const ended = [...this.calls.entries()].filter(
      ([, m]) => m.record.state === 'ENDED' || m.record.state === 'FAILED',
    );
    const overflow = ended.length - ENDED_HISTORY_LIMIT;
    for (let i = 0; i < overflow; i += 1) {
      const entry = ended[i];
      if (entry) {
        this.detachSession(entry[1]);
        this.calls.delete(entry[0]);
      }
    }
  }

  private emit<T extends VoiceEventType>(type: T, payload: Parameters<typeof createVoiceEvent<T>>[1]): void {
    const event = createVoiceEvent(type, payload);
    this.listeners.get(type)?.forEach((listener) => {
      (listener as VoiceEventListener<T>)(event);
    });
  }
}

function validateConnection(connection: GenericSipConnectionConfig): void {
  if (!connection || !connection.endpoint?.trim() || !connection.sipDomain?.trim() || !connection.username?.trim()) {
    throw new VoiceError(
      'VOICE_NOT_CONFIGURED',
      'Invalid SIP configuration: WSS endpoint, SIP domain, and username are required.',
    );
  }
  if (!/^wss?:\/\/.+/.test(connection.endpoint.trim())) {
    throw new VoiceError(
      'VOICE_NOT_CONFIGURED',
      'Invalid SIP configuration: endpoint must be a ws(s):// WebSocket URL (wss:// in production).',
    );
  }
}

function toPeerConnectionConfig(connection: GenericSipConnectionConfig): RTCConfiguration {
  if (!connection.iceServers || connection.iceServers.length === 0) return {};
  return {
    iceServers: connection.iceServers.map((server) => {
      const entry: RTCIceServer = { urls: server.urls };
      if (server.username) entry.username = server.username;
      // ponytail: TURN credential passthrough only; never the SIP secret.
      if (server.credential) entry.credential = server.credential;
      return entry;
    }),
  };
}

function setLocalAudioEnabled(session: Session, enabled: boolean): void {
  const sdh = session.sessionDescriptionHandler as unknown as WebSdh | null;
  let touched = false;
  try {
    sdh?.peerConnection?.getSenders().forEach((sender) => {
      if (sender.track?.kind === 'audio') {
        sender.track.enabled = enabled;
        touched = true;
      }
    });
    if (!touched) {
      sdh?.localMediaStream?.getAudioTracks().forEach((track) => {
        track.enabled = enabled;
      });
    }
  } catch {
    /* noop — mute is best-effort on track flags; session stays up */
  }
}
