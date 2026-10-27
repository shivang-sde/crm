/**
 * VOICE-03 — main-window side softphone controller.
 * VOICE-04 — hardened: link lifecycle + gating, popup generation identity,
 * snapshot sequencing, source validation, prompt disappearance cleanup.
 *
 * Opens/focuses the single softphone popup (stable window name), performs
 * the handshake, sends typed commands with timeout + requestId correlation,
 * and receives forwarded voice events. Never touches SIP.js, secrets, or
 * WebRTC — the popup owns the session.
 */
import {
  SOFTPHONE_CHANNEL,
  SOFTPHONE_COMMAND_TIMEOUT_MS,
  SOFTPHONE_HANDSHAKE_TIMEOUT_MS,
  SOFTPHONE_POPUP_URL,
  SOFTPHONE_PRESENCE_TTL_MS,
  SOFTPHONE_PROTOCOL_VERSION,
  SOFTPHONE_ROUTE,
  SOFTPHONE_WINDOW_NAME,
  SoftphoneError,
  isPreReadyCommand,
  isSoftphoneMessage,
  isSupportedProtocolVersion,
  isWrongVersionMessage,
  readSoftphonePresence,
  softphoneOrigin,
  softphoneTabId,
  type CommandResultPayload,
  type PopupCommandPayloads,
  type PopupCommandType,
  type SoftphoneCommandMessage,
  type SoftphoneLinkState,
  type SoftphoneSnapshot,
  type VoiceForwardPayload,
} from './protocol';

export interface SoftphoneControllerOptions {
  commandTimeoutMs?: number;
  handshakeTimeoutMs?: number;
  popupFeatures?: string;
}

interface PendingCommand {
  resolve: (data: CommandResultPayload['data']) => void;
  reject: (error: Error) => void;
  timer: ReturnType<typeof setTimeout>;
}

export type SoftphoneEventListener = (forward: VoiceForwardPayload) => void;
export type SoftphoneSnapshotListener = (snapshot: SoftphoneSnapshot) => void;

function newRequestId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `req-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

function buildCommand<T extends PopupCommandType>(
  type: T,
  requestId: string,
  payload: PopupCommandPayloads[T],
): SoftphoneCommandMessage {
  // Safe: the generic signature correlates `type` <-> `payload`.
  return {
    channel: SOFTPHONE_CHANNEL,
    version: SOFTPHONE_PROTOCOL_VERSION,
    kind: 'command',
    type,
    requestId,
    payload,
  } as SoftphoneCommandMessage;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Open (or focus) the single softphone popup. Stable name: no duplicates. */
export function openSoftphonePopup(features = 'width=380,height=680,menubar=no,toolbar=no,location=no'): Window | null {
  if (typeof window === 'undefined') return null;
  // Constant hash URL: re-acquiring an existing popup is same-document
  // (no reload, SIP session survives). Never use the bare route here.
  return window.open(SOFTPHONE_POPUP_URL, SOFTPHONE_WINDOW_NAME, features);
}

export class SoftphoneController {
  private popup: Window | null = null;
  private readonly pending = new Map<string, PendingCommand>();
  private readonly eventListeners = new Set<SoftphoneEventListener>();
  private readonly snapshotListeners = new Set<SoftphoneSnapshotListener>();
  private lastSnapshot: SoftphoneSnapshot | null = null;
  private listening = false;
  private disposed = false;
  private linkState: SoftphoneLinkState = 'DISCONNECTED';
  /** Accepted popup generation; stale generations can never overwrite state. */
  private acceptedInstanceId: string | null = null;
  /**
   * Generation history (newest last, capped): distinguishes a genuinely new
   * generation (adopt + rebaseline) from a stale replay (ignore). Random
   * ids are unordered, so recency must be remembered explicitly.
   */
  private readonly seenGenerations: string[] = [];
  private lastSequence = -1;
  private autoRehandshake = false;
  /**
   * Handshake generation guards the failure downgrade: a handshake that
   * times out must not clobber READY established by a concurrent handshake
   * (explicit retry vs auto-rehandshake on slow popup boot). Downgrade only
   * when no handshake completed at or after this call started.
   */
  private handshakeSeq = 0;
  private lastHandshakeOkSeq = 0;
  private readonly commandTimeoutMs: number;
  private readonly handshakeTimeoutMs: number;
  private readonly popupFeatures: string;

  constructor(options?: SoftphoneControllerOptions) {
    this.commandTimeoutMs = options?.commandTimeoutMs ?? SOFTPHONE_COMMAND_TIMEOUT_MS;
    this.handshakeTimeoutMs = options?.handshakeTimeoutMs ?? SOFTPHONE_HANDSHAKE_TIMEOUT_MS;
    this.popupFeatures = options?.popupFeatures ?? 'width=380,height=680,menubar=no,toolbar=no,location=no';
  }

  /** Open the popup (or focus it when already open). Throws when blocked. */
  open(): Window {
    this.assertUsable();
    if (typeof window === 'undefined') {
      throw new SoftphoneError('SOFTPHONE_POPUP_UNAVAILABLE', 'Softphone requires a browser window.');
    }
    // Live reference: focus only — never re-navigate a running session.
    // If the window no longer hosts the softphone (e.g. navigated away),
    // reclaim the named slot explicitly; a blank/dead page holds no session.
    if (this.popup && !this.popup.closed) {
      this.reclaimIfNotSoftphone(this.popup);
      try {
        this.popup.focus();
      } catch {
        /* focus is best-effort */
      }
      this.ensureListening();
      return this.popup;
    }
    // Foreign live popup (owned by another tab): refuse a second SIP runtime.
    // Same-tab reload keeps the tab id (sessionStorage), so re-attach works.
    // Adoptable when the owner is gone (popup clears ownerTabId) or stale.
    const presence = readSoftphonePresence();
    if (presence && Date.now() - presence.updatedAt < SOFTPHONE_PRESENCE_TTL_MS) {
      const mine = softphoneTabId();
      if (mine && presence.ownerTabId && presence.ownerTabId !== mine) {
        throw new SoftphoneError(
          'SOFTPHONE_POPUP_BLOCKED',
          'The softphone is already open in another browser tab. Use that tab to avoid a second calling session.',
        );
      }
    }
    // Fresh controller (e.g. after CRM reload): the constant hash makes this
    // same-document for an existing popup — focus without reload — and a
    // normal load when creating it for the first time.
    const win = window.open(SOFTPHONE_POPUP_URL, SOFTPHONE_WINDOW_NAME, this.popupFeatures);
    if (!win) {
      throw new SoftphoneError('SOFTPHONE_POPUP_BLOCKED', 'The softphone popup was blocked. Allow popups and try again.');
    }
    this.popup = win;
    try {
      win.focus();
    } catch {
      /* focus is best-effort */
    }
    this.ensureListening();
    this.setLinkState('CONNECTING');
    return win;
  }

  get linkStateSnapshot(): SoftphoneLinkState {
    return this.linkState;
  }

  get isPopupOpen(): boolean {
    return !!this.popup && !this.popup.closed;
  }

  get snapshot(): SoftphoneSnapshot | null {
    return this.lastSnapshot;
  }

  /**
   * Handshake with the popup (safe to repeat after CRM reload or tab switch).
   * Retries until the deadline so both freshly-opened and long-running
   * popups attach. Resolves with the popup's state snapshot — never secrets.
   */
  async handshake(connectorInstanceId?: string): Promise<SoftphoneSnapshot> {
    this.open();
    this.setLinkState('HANDSHAKING');
    const mySeq = ++this.handshakeSeq;
    const senderTabId = softphoneTabId() ?? undefined;
    const deadline = Date.now() + this.handshakeTimeoutMs;
    let lastError: unknown = null;
    while (Date.now() < deadline) {
      if (!this.isPopupOpen) {
        lastError = new SoftphoneError('SOFTPHONE_POPUP_UNAVAILABLE', 'Softphone popup closed during handshake.');
        break;
      }
      try {
        const data = await this.sendCommand('HANDSHAKE', { connectorInstanceId, senderTabId }, 2000);
        const snapshot = data as SoftphoneSnapshot | null;
        if (!snapshot || typeof snapshot !== 'object' || !snapshot.voice) {
          throw new SoftphoneError('SOFTPHONE_PROTOCOL_MISMATCH', 'Softphone handshake returned an invalid snapshot.');
        }
        this.acceptBaseline(snapshot);
        this.lastHandshakeOkSeq = mySeq;
        this.setLinkState('READY');
        return snapshot;
      } catch (error) {
        lastError = error;
        await sleep(500);
      }
    }
    // A concurrent handshake may have reached READY after this call started
    // (slow popup boot + auto-rehandshake); never downgrade newer truth.
    if (this.lastHandshakeOkSeq < mySeq) this.setLinkState('DISCONNECTED');
    if (lastError instanceof SoftphoneError) throw lastError;
    throw new SoftphoneError('SOFTPHONE_HANDSHAKE_TIMEOUT', 'Softphone popup did not respond.');
  }

  async sendCommand<T extends PopupCommandType>(
    type: T,
    payload: PopupCommandPayloads[T],
    timeoutMs?: number,
  ): Promise<CommandResultPayload['data']> {
    this.assertUsable();
    if (!this.isPopupOpen) {
      // Prompt cleanup: a dead popup fails everything pending, not just this.
      this.failPending(
        new SoftphoneError('SOFTPHONE_POPUP_UNAVAILABLE', 'Softphone popup is not open.'),
      );
      this.setLinkState('DISCONNECTED');
      throw new SoftphoneError('SOFTPHONE_POPUP_UNAVAILABLE', 'Softphone popup is not open.');
    }
    // Command gating (§7): only discovery primitives may fly pre-READY.
    // Safety-sensitive voice commands are rejected deterministically —
    // never queued, never silently dropped.
    if (this.linkState !== 'READY' && !isPreReadyCommand(type)) {
      throw new SoftphoneError(
        'SOFTPHONE_POPUP_UNAVAILABLE',
        `Softphone is not ready for ${type}. Handshake first.`,
      );
    }
    const origin = softphoneOrigin();
    const popup = this.popup;
    if (!popup || !origin) {
      throw new SoftphoneError('SOFTPHONE_POPUP_UNAVAILABLE', 'Softphone popup is not open.');
    }
    const requestId = newRequestId();
    const message = buildCommand(type, requestId, payload);
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(requestId);
        reject(new SoftphoneError('SOFTPHONE_COMMAND_TIMEOUT', `Softphone command timed out: ${type}.`));
      }, timeoutMs ?? this.commandTimeoutMs);
      this.pending.set(requestId, { resolve, reject, timer });
      try {
        popup.postMessage(message, origin);
      } catch (error) {
        this.pending.delete(requestId);
        clearTimeout(timer);
        reject(
          new SoftphoneError(
            'SOFTPHONE_POPUP_UNAVAILABLE',
            `Unable to reach the softphone popup: ${error instanceof Error ? error.message : 'unknown error'}.`,
          ),
        );
      }
    });
  }

  onVoiceEvent(listener: SoftphoneEventListener): () => void {
    this.eventListeners.add(listener);
    return () => {
      this.eventListeners.delete(listener);
    };
  }

  onSnapshot(listener: SoftphoneSnapshotListener): () => void {
    this.snapshotListeners.add(listener);
    return () => {
      this.snapshotListeners.delete(listener);
    };
  }

  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    if (typeof window !== 'undefined') {
      window.removeEventListener('message', this.onMessage);
    }
    this.listening = false;
    this.failPending(
      new SoftphoneError('SOFTPHONE_POPUP_UNAVAILABLE', 'Softphone controller was disposed.'),
    );
    this.eventListeners.clear();
    this.snapshotListeners.clear();
    this.popup = null;
    this.setLinkState('CLOSED');
  }

  // ---------- private ----------

  private assertUsable(): void {
    if (this.disposed) throw new SoftphoneError('SOFTPHONE_POPUP_UNAVAILABLE', 'Softphone controller was disposed.');
  }

  private reclaimIfNotSoftphone(win: Window): void {
    let path = '';
    try {
      const url = new URL(win.location.href);
      if (url.origin !== softphoneOrigin()) return;
      path = url.pathname;
    } catch {
      // about:blank or unreadable: no session can live here — reclaim below.
      path = '';
    }
    if (path !== SOFTPHONE_ROUTE) {
      try {
        win.location.href = SOFTPHONE_POPUP_URL;
      } catch {
        /* cross-window navigation failed — handshake will time out */
      }
    }
  }

  private ensureListening(): void {
    if (this.listening || typeof window === 'undefined') return;
    this.listening = true;
    window.addEventListener('message', this.onMessage);
  }

  private readonly onMessage = (event: MessageEvent): void => {
    const origin = softphoneOrigin();
    if (origin === null || event.origin !== origin) return;
    // Unsupported protocol version: reject deterministically instead of
    // hanging until timeout. Structural read only — never trusted further.
    if (isWrongVersionMessage(event.data)) {
      this.rejectWrongVersion(event.data);
      return;
    }
    if (!isSoftphoneMessage(event.data)) return;
    // Source validation: only our own popup window is trusted. Reloads
    // preserve the WindowProxy identity, so this survives popup reloads
    // while rejecting foreign tabs, iframes, and forged dispatches.
    if (event.source !== this.popup) return;
    const msg = event.data;
    if (msg.kind === 'event' && msg.type === 'POPUP_READY') {
      this.onPopupReady(msg.payload.popupInstanceId);
      return;
    }
    if (msg.kind === 'event' && msg.type === 'VOICE_EVENT') {
      this.onVoiceForward(msg.payload.snapshot, msg.payload);
      return;
    }
    if (msg.kind === 'result' && msg.type === 'COMMAND_RESULT') {
      this.onCommandResult(msg.payload);
    }
  };

  private rejectWrongVersion(value: unknown): void {
    const data = value as { kind?: unknown; type?: unknown; requestId?: unknown };
    if (data.kind !== 'result') return;
    const requestId = data.requestId;
    if (typeof requestId !== 'string') return;
    const pending = this.pending.get(requestId);
    if (!pending) return;
    this.pending.delete(requestId);
    clearTimeout(pending.timer);
    pending.reject(
      new SoftphoneError('SOFTPHONE_PROTOCOL_MISMATCH', 'Softphone protocol version is not supported.'),
    );
  }

  /** Adopt a popup generation baseline (handshake or first-seen event). */
  private acceptBaseline(snapshot: SoftphoneSnapshot): void {
    this.acceptedInstanceId = snapshot.popupInstanceId;
    this.rememberGeneration(snapshot.popupInstanceId);
    this.lastSequence = snapshot.sequence;
    this.setSnapshot(snapshot);
  }

  private rememberGeneration(instanceId: string): void {
    const at = this.seenGenerations.indexOf(instanceId);
    if (at >= 0) this.seenGenerations.splice(at, 1);
    this.seenGenerations.push(instanceId);
    while (this.seenGenerations.length > 20) this.seenGenerations.shift();
  }

  /**
   * A new generation appeared (reload). Old pending belongs to a dead
   * runtime: fail it, reset the baseline, re-handshake to READY.
   */
  private onPopupReady(instanceId: unknown): void {
    if (typeof instanceId !== 'string' || instanceId.length === 0) return;
    if (instanceId === this.acceptedInstanceId) return;
    // Replay of a dead generation's READY: ignore (never re-handshake stale).
    if (this.seenGenerations.includes(instanceId)) return;
    this.failPending(
      new SoftphoneError('SOFTPHONE_POPUP_UNAVAILABLE', 'Softphone popup restarted; previous requests were dropped.'),
    );
    this.acceptedInstanceId = instanceId;
    this.rememberGeneration(instanceId);
    this.lastSequence = -1;
    if (this.disposed || this.autoRehandshake) return;
    this.autoRehandshake = true;
    this.setLinkState('HANDSHAKING');
    void this.handshake()
      .catch(() => {
        if (!this.disposed) this.setLinkState('DISCONNECTED');
      })
      .finally(() => {
        this.autoRehandshake = false;
      });
  }

  private onVoiceForward(snapshot: SoftphoneSnapshot, forward: VoiceForwardPayload): void {
    if (!snapshot || typeof snapshot !== 'object') return;
    if (typeof snapshot.popupInstanceId !== 'string' || typeof snapshot.sequence !== 'number') return;    // First contact ever: adopt the authoritative baseline directly.
    if (this.acceptedInstanceId === null) {
      this.acceptBaseline(snapshot);
      if (this.linkState !== 'READY') this.setLinkState('HANDSHAKING');
      this.eventListeners.forEach((listener) => listener(forward));
      return;
    }
    // Stale replay of a dead generation: ignore unconditionally.
    if (snapshot.popupInstanceId !== this.acceptedInstanceId) {
      if (this.seenGenerations.includes(snapshot.popupInstanceId)) return;
      // Unknown generation (e.g. READY was lost in transit): do NOT adopt a
      // possibly forged payload — refresh truth via handshake instead.
      if (!this.autoRehandshake && !this.disposed) {
        this.autoRehandshake = true;
        this.setLinkState('HANDSHAKING');
        void this.handshake()
          .catch(() => {
            if (!this.disposed) this.setLinkState('DISCONNECTED');
          })
          .finally(() => {
            this.autoRehandshake = false;
          });
      }
      return;
    }
    // Stale or duplicate delivery: ignore, never overwrite newer state.
    if (snapshot.sequence <= this.lastSequence) return;
    this.lastSequence = snapshot.sequence;
    this.setSnapshot(snapshot);
    this.eventListeners.forEach((listener) => listener(forward));
  }

  private onCommandResult(payload: CommandResultPayload): void {
    const pending = this.pending.get(payload.requestId);
    if (!pending) return; // duplicate, timed-out, or unknown — ignore safely.
    // Stale/foreign generation result: never resolve with it. First-contact
    // results (no baseline yet) are accepted.
    if (
      payload.popupInstanceId !== undefined &&
      this.acceptedInstanceId !== null &&
      payload.popupInstanceId !== this.acceptedInstanceId
    ) {
      return;
    }
    this.pending.delete(payload.requestId);
    clearTimeout(pending.timer);
    if (payload.ok) {
      pending.resolve(payload.data ?? null);
    } else {
      pending.reject(
        new SoftphoneError(
          'SOFTPHONE_COMMAND_REJECTED',
          payload.error?.message ?? 'Softphone command failed.',
          payload.error?.code,
        ),
      );
    }
  }

  private setSnapshot(snapshot: SoftphoneSnapshot): void {
    this.lastSnapshot = snapshot;
    this.snapshotListeners.forEach((listener) => listener(snapshot));
  }

  private setLinkState(state: SoftphoneLinkState): void {
    this.linkState = state;
  }

  private failPending(error: SoftphoneError): void {
    if (this.pending.size === 0) return;
    for (const [, pending] of this.pending) {
      clearTimeout(pending.timer);
      pending.reject(error);
    }
    this.pending.clear();
  }
}
