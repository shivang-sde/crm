/**
 * VOICE-07 FIX-15 — hold/resume re-INVITE diagnostics (diagnostic only).
 *
 * Observes what the EXISTING `GenericSipAdapter.hold()/resume()` actually
 * does. It changes nothing: every collector here is read-only, and the
 * adapter only calls into this module while diagnostics are enabled
 * (`?diag=1`, wired through `SoftphoneHost`). No new SIP session, no new
 * PeerConnection, no new audio element, no SDP mutation.
 *
 * Self-contained on purpose — no imports — so `tests/voice-stats.check.cjs`
 * can compile and exercise it standalone with no browser and no network.
 *
 * SECRET / PII DISCIPLINE: SDP is summarized, never dumped. ICE ufrag/pwd
 * values, fingerprint values, candidate lines and connection addresses are
 * reported as booleans or counts ONLY. Dialog identity is reduced to a short
 * salted-free hash-free shape (call-id suffix), never a full identifier.
 */

/** Marker for a metric the browser does not expose. Never `0`, never `null`. */
export const UNAVAILABLE = 'UNAVAILABLE' as const;
export type Unavailable = typeof UNAVAILABLE;

export type HoldOperation = 'HOLD' | 'RESUME';

export type HoldStage =
  | 'COMMAND_STARTED'
  | 'REINVITE_STARTED'
  | 'REINVITE_REQUEST_SENT'
  | 'PROVISIONAL_RESPONSE'
  | 'FINAL_RESPONSE'
  | 'REINVITE_ACCEPTED'
  | 'REINVITE_REJECTED'
  | 'REINVITE_FAILED'
  | 'COMMAND_COMPLETED';

/** Lifecycle stages that are real SIP.js/Session observations. */
export const HOLD_STAGES: readonly HoldStage[] = [
  'COMMAND_STARTED',
  'REINVITE_STARTED',
  'REINVITE_REQUEST_SENT',
  'PROVISIONAL_RESPONSE',
  'FINAL_RESPONSE',
  'REINVITE_ACCEPTED',
  'REINVITE_REJECTED',
  'REINVITE_FAILED',
  'COMMAND_COMPLETED',
];

/** §4 — SIP.js sends the UAC re-INVITE ACK internally; no public event. */
export const ACK_OBSERVATION = 'NOT_DIRECTLY_EXPOSED' as const;

export type MediaDirection = 'sendrecv' | 'sendonly' | 'recvonly' | 'inactive' | 'absent';

export interface CodecEntry {
  payloadType: number;
  name: string;
  clockRate: number | null;
  channels: number | null;
}

/**
 * Redacted SDP summary. Contains negotiation STRUCTURE only.
 * Never contains: ice ufrag/pwd values, fingerprint value, `a=candidate:`
 * lines, `c=` addresses, or any raw SDP line.
 */
export interface SdpSummary {
  present: boolean;
  /** First audio m-section only — the only media type this app uses. */
  hasAudio: boolean;
  direction: MediaDirection;
  /** Payload types from the audio m-line, in order. */
  payloadTypes: number[];
  codecs: CodecEntry[];
  rtcpMux: boolean;
  /** `a=ice-ufrag:` present. The value is never emitted. */
  iceUfragPresent: boolean;
  /** `a=ice-pwd:` present. The value is never emitted. */
  icePwdPresent: boolean;
  iceRestartRequested: boolean;
  /** Count only — candidate lines are never emitted. */
  candidateCount: number;
  /** Any `c=` line present. The address is never emitted. */
  connectionAddressPresent: boolean;
  mediaPort: number | null;
  mediaProtocol: string | null;
  setup: string | null;
  mid: string | null;
  /** `a=fingerprint:` present. The value is never emitted. */
  fingerprintPresent: boolean;
  /** `a=msid:<streamId> <trackId>` — random per-session UUIDs, not secrets. */
  msidStreamId: string | null;
  msidTrackId: string | null;
  dtmfEvents: number[];
}

export interface TransceiverSummary {
  mid: string | null;
  kind: string | null;
  direction: string | null;
  currentDirection: string | null;
  stopped: boolean;
  senderTrackKind: string | null;
  senderTrackEnabled: boolean | null;
  senderTrackReadyState: string | null;
  receiverTrackId: string | null;
  receiverTrackKind: string | null;
  receiverTrackEnabled: boolean | null;
  receiverTrackMuted: boolean | null;
  receiverTrackReadyState: string | null;
}

export interface PeerConnectionSummary {
  present: boolean;
  connectionState: string | null;
  iceConnectionState: string | null;
  signalingState: string | null;
  iceGatheringState: string | null;
  senderCount: number;
  receiverCount: number;
  transceivers: TransceiverSummary[];
  /** Reused/candidate ICE state that decides whether re-INVITE can proceed. */
  reinvitePending: boolean | null;
  /** Modifier count still installed on the Session for re-INVITEs. */
  reinviteModifiersInstalled: number | null;
  /** True when the installed modifier set still contains the hold modifier. */
  holdModifierStillInstalled: boolean | null;
}

export interface RemoteMediaSummary {
  present: boolean;
  remoteMediaStreamId: string | null;
  streamTrackCount: number;
  trackId: string | null;
  trackKind: string | null;
  trackMuted: boolean | null;
  trackEnabled: boolean | null;
  trackReadyState: string | null;
  /** Receiver-derived fallback when remoteMediaStream is absent. */
  source: 'remoteMediaStream' | 'receiver' | 'none';
}

export interface AudioElementSummary {
  present: boolean;
  srcObjectPresent: boolean;
  srcObjectId: string | null;
  srcObjectTrackIds: string[];
  paused: boolean;
  muted: boolean;
  volume: number;
  readyState: number;
  networkState: number;
  currentTime: number;
  sinkId: string;
  sinkSupported: boolean;
}

export interface RtpSummary {
  present: boolean;
  ssrc: number | null;
  codec: string | null;
  payloadType: string | null;
  packetsReceived: number | null;
  bytesReceived: number | null;
  packetsLost: number | null;
  jitter: number | null;
  audioLevel: number | null;
  totalAudioEnergy: number | null;
  totalSamplesReceived: number | null;
  jitterBufferEmittedCount: number | null;
  concealedSamples: number | null;
  lastPacketReceivedTimestamp: number | null;
  timestamp: number;
  /** Selected candidate pair + RTT where the browser exposes them. */
  candidatePairState: string | Unavailable;
  localCandidateType: string | Unavailable;
  remoteCandidateType: string | Unavailable;
  rttSeconds: number | null;
  availableLocalCandidateTypes: string[];
}

export interface SipResponseSummary {
  statusCode: number | null;
  reasonPhrase: string | null;
  cseq: string | null;
  /** `last 8 chars` of the dialog Call-ID — enough to correlate, not an id. */
  dialogTagSuffix: string | null;
}

export interface Snapshot {
  label: string;
  at: number;
  sessionState: string | null;
  adapterCallState: string | null;
  held: boolean | null;
  pc: PeerConnectionSummary;
  remote: RemoteMediaSummary;
  element: AudioElementSummary | null;
  rtp: RtpSummary | null;
}

export interface HoldTraceEvent {
  at: number;
  stage: HoldStage | string;
  detail: Record<string, unknown>;
}

export interface HoldTrace {
  transactionId: string;
  operation: HoldOperation;
  callId: string;
  startedAt: number;
  completedAt: number | null;
  durationMs: number | null;
  holdStateBefore: boolean | null;
  holdStateAfter: boolean | null;
  ackObservation: typeof ACK_OBSERVATION;
  events: HoldTraceEvent[];
  before: Snapshot | null;
  after: Snapshot | null;
  timeline: Snapshot[];
  outgoingSdp: SdpSummary | null;
  answerSdp: SdpSummary | null;
  /** Offer the browser actually produced, before any SIP.js modifier. */
  preModifierOfferSdp: SdpSummary | null;
  responses: SipResponseSummary[];
  error: { name: string; message: string; statusCode: number | null; kind: string } | null;
  classification: HoldClassification | null;
}

export type HoldClassificationCode =
  | 'SIP_REINVITE_REJECTED'
  | 'SDP_DIRECTION_NOT_RESTORED'
  | 'RTP_MEDIA_PATH_FAILURE_AFTER_RESUME'
  | 'BROWSER_OUTPUT_PATH_SUSPECTED'
  | 'REMOTE_TRACK_LIFECYCLE_CHANGE'
  | 'PEER_CONNECTION_STATE_CHANGE'
  | 'REINVITE_PENDING_OR_STALLED'
  | 'NO_APP_LAYER_FAILURE_DETECTED';

export interface HoldClassification {
  primary: HoldClassificationCode;
  secondary: HoldClassificationCode[];
  /** Evidence lines — every claim must be traceable to an observation. */
  evidence: string[];
  /** §11 D: never assert audibility from a metric alone. */
  requiresHumanAudibilityConfirmation: boolean;
}

// ---------------------------------------------------------------------------
// redaction helpers
// ---------------------------------------------------------------------------

function asText(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function numOrNull(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function boolOrNull(value: unknown): boolean | null {
  return typeof value === 'boolean' ? value : null;
}

/**
 * Last-resort scrub applied to anything captured from SIP.js before it can
 * enter a trace. Mirrors the adapter's existing `sanitize()` plus SDP-shaped
 * redaction, so a new SIP.js error string can never leak a credential.
 */
export function scrubDiagnosticText(input: string): string {
  return input
    .replace(/:\/\/[^/\s]+:[^/\s]+@/g, '://***:***@')
    .replace(/["']?password["']?\s*[:=]\s*["']?[^"',;}\s]+["']?/gi, '"password":"***"')
    .replace(/Authorization:[^\r\n]*/gi, 'Authorization: ***')
    .replace(/a=ice-(?:ufrag|pwd):[^\r\n]*/gi, 'a=ice-$1:<redacted>')
    .replace(/a=fingerprint:[^\r\n]*/gi, 'a=fingerprint:<redacted>')
    .replace(/a=candidate:[^\r\n]*/gi, 'a=candidate:<redacted>')
    .replace(/c=IN[^\r\n]*/gi, 'c=IN <redacted>')
    .slice(0, 300);
}

function sdpLines(sdp: string): string[] {
  return sdp.split(/\r\n|\n|\r/).map((l) => l.trim()).filter((l) => l.length > 0);
}

/** Index of the audio m-section, or -1. */
function audioSectionStart(lines: string[]): number {
  return lines.findIndex((l) => /^m=audio\b/.test(l));
}

/** Lines belonging to the audio m-section (until the next m=). */
function audioSectionLines(lines: string[]): string[] {
  const start = audioSectionStart(lines);
  if (start < 0) return [];
  const out: string[] = [];
  for (let idx = start; idx < lines.length; idx += 1) {
    if (idx > start && /^m=/.test(lines[idx]!)) break;
    out.push(lines[idx]!);
  }
  return out;
}

/**
 * §6 — redacted SDP summary. STRUCTURE ONLY.
 *
 * Emits booleans/counts for anything that could carry network topology or a
 * credential (ICE ufrag/pwd, DTLS fingerprint, candidates, `c=` address).
 */
export function summarizeSdp(sdp: string | null | undefined): SdpSummary {
  const empty: SdpSummary = {
    present: false,
    hasAudio: false,
    direction: 'absent',
    payloadTypes: [],
    codecs: [],
    rtcpMux: false,
    iceUfragPresent: false,
    icePwdPresent: false,
    iceRestartRequested: false,
    candidateCount: 0,
    connectionAddressPresent: false,
    mediaPort: null,
    mediaProtocol: null,
    setup: null,
    mid: null,
    fingerprintPresent: false,
    msidStreamId: null,
    msidTrackId: null,
    dtmfEvents: [],
  };
  if (typeof sdp !== 'string' || sdp.trim().length === 0) return empty;

  const all = sdpLines(sdp);
  const section = audioSectionLines(all);
  if (section.length === 0) {
    return { ...empty, present: true };
  }

  const mLine = /^m=audio\s+(\d+)\s+(\S+)\s*(.*)$/.exec(section[0]!);
  const payloadTypes: number[] = [];
  if (mLine) {
    for (const tok of (mLine[3] ?? '').split(/\s+/)) {
      const n = Number(tok);
      if (Number.isInteger(n) && n >= 0 && n < 128) payloadTypes.push(n);
    }
  }

  const codecs: CodecEntry[] = [];
  const dtmfEvents: number[] = [];
  let direction: MediaDirection = 'absent';
  let setup: string | null = null;
  let mid: string | null = null;
  let msidStreamId: string | null = null;
  let msidTrackId: string | null = null;

  for (const line of section) {
    const dir = /^a=(sendrecv|sendonly|recvonly|inactive)$/.exec(line);
    if (dir) {
      // RFC 4566 §6.1: within one m-section the first direction attribute wins.
      if (direction === 'absent') direction = dir[1] as MediaDirection;
      continue;
    }
    const rtpmap = /^a=rtpmap:(\d+)\s+([^/]+)\/(\d+)(?:\/(\d+))?$/.exec(line);
    if (rtpmap) {
      codecs.push({
        payloadType: Number(rtpmap[1]),
        name: rtpmap[2]!,
        clockRate: numOrNull(Number(rtpmap[3])),
        channels: numOrNull(rtpmap[4] === undefined ? 1 : Number(rtpmap[4])),
      });
      if (rtpmap[2] === 'telephone-event') dtmfEvents.push(Number(rtpmap[1]));
      continue;
    }
    const msid = /^a=msid:(\S+)(?:\s+(\S+))?$/.exec(line);
    if (msid && msidStreamId === null) {
      msidStreamId = msid[1]!;
      msidTrackId = msid[2] ?? null;
      continue;
    }
    const s = /^a=setup:(\S+)$/.exec(line);
    if (s && setup === null) setup = s[1]!;
    const m = /^a=mid:(\S+)$/.exec(line);
    if (m && mid === null) mid = m[1]!;
  }

  // RFC 4566: a media section with no direction attribute is sendrecv.
  const effective: MediaDirection = direction === 'absent' ? 'sendrecv' : direction;

  return {
    present: true,
    hasAudio: true,
    direction: effective,
    payloadTypes,
    codecs,
    rtcpMux: section.some((l) => l === 'a=rtcp-mux'),
    iceUfragPresent: section.some((l) => l.startsWith('a=ice-ufrag:')),
    icePwdPresent: section.some((l) => l.startsWith('a=ice-pwd:')),
    iceRestartRequested: /^a=ice-restart\s*$/m.test(sdp),
    candidateCount: all.filter((l) => l.startsWith('a=candidate:')).length,
    connectionAddressPresent: all.some((l) => l.startsWith('c=')),
    mediaPort: mLine ? Number(mLine[1]) : null,
    mediaProtocol: mLine ? (mLine[2] ?? null) : null,
    setup,
    mid,
    fingerprintPresent: all.some((l) => l.startsWith('a=fingerprint:')),
    msidStreamId,
    msidTrackId,
    dtmfEvents,
  };
}

// ---------------------------------------------------------------------------
// structural views (no SIP.js / DOM type imports)
// ---------------------------------------------------------------------------

interface SdhView {
  remoteMediaStream?: { id?: string; getTracks?: () => MediaStreamTrack[] };
  peerConnection?: unknown;
}

interface PcView {
  connectionState?: string;
  iceConnectionState?: string;
  signalingState?: string;
  iceGatheringState?: string;
  getTransceivers?: () => TransceiverView[];
  getSenders?: () => unknown[];
  getReceivers?: () => ReceiverView[];
  getStats?: () => Promise<{ forEach: (cb: (r: unknown) => void) => void }>;
}

interface TransceiverView {
  mid?: string | null;
  currentDirection?: string | null;
  direction?: string;
  stopped?: boolean;
  sender?: { track?: { kind?: string; enabled?: boolean; readyState?: string } | null };
  receiver?: { track?: MediaStreamTrack | null };
}

interface ReceiverView {
  track?: MediaStreamTrack | null;
}

function asPc(value: unknown): PcView | null {
  return value && typeof value === 'object' ? (value as PcView) : null;
}

function summarizeTrack(track: MediaStreamTrack | null | undefined) {
  return {
    id: typeof track?.id === 'string' ? track.id : null,
    kind: asText(track?.kind) || null,
    muted: boolOrNull(track?.muted),
    enabled: boolOrNull(track?.enabled),
    readyState: asText(track?.readyState) || null,
  };
}

/**
 * §7 — observation only. Never creates, replaces, enables or disables a
 * track, and never touches a transceiver's direction.
 */
export function collectPeerConnectionState(
  pc: unknown,
  extras?: { reinvitePending?: boolean; modifiers?: unknown[]; holdModifier?: unknown },
): PeerConnectionSummary {
  const view = asPc(pc);
  if (!view) {
    return {
      present: false,
      connectionState: null,
      iceConnectionState: null,
      signalingState: null,
      iceGatheringState: null,
      senderCount: 0,
      receiverCount: 0,
      transceivers: [],
      reinvitePending: extras?.reinvitePending ?? null,
      reinviteModifiersInstalled: extras?.modifiers ? extras.modifiers.length : null,
      holdModifierStillInstalled:
        extras?.holdModifier !== undefined && extras?.modifiers
          ? extras.modifiers.includes(extras.holdModifier)
          : null,
    };
  }
  let transceivers: TransceiverView[] = [];
  try {
    transceivers = view.getTransceivers?.() ?? [];
  } catch {
    transceivers = [];
  }
  return {
    present: true,
    connectionState: view.connectionState ?? null,
    iceConnectionState: view.iceConnectionState ?? null,
    signalingState: view.signalingState ?? null,
    iceGatheringState: view.iceGatheringState ?? null,
    senderCount: safeCount(view.getSenders),
    receiverCount: safeCount(view.getReceivers),
    transceivers: transceivers.map((t) => {
      const r = summarizeTrack(t.receiver?.track);
      return {
        mid: t.mid ?? null,
        kind: asText(t.sender?.track?.kind) || asText(t.receiver?.track?.kind) || null,
        direction: t.direction ?? null,
        currentDirection: t.currentDirection ?? null,
        stopped: t.stopped === true,
        senderTrackKind: asText(t.sender?.track?.kind) || null,
        senderTrackEnabled: boolOrNull(t.sender?.track?.enabled),
        senderTrackReadyState: asText(t.sender?.track?.readyState) || null,
        receiverTrackId: r.id,
        receiverTrackKind: r.kind,
        receiverTrackEnabled: r.enabled,
        receiverTrackMuted: r.muted,
        receiverTrackReadyState: r.readyState,
      };
    }),
    reinvitePending: extras?.reinvitePending ?? null,
    reinviteModifiersInstalled: extras?.modifiers ? extras.modifiers.length : null,
    holdModifierStillInstalled:
      extras?.holdModifier !== undefined && extras?.modifiers
        ? extras.modifiers.includes(extras.holdModifier)
        : null,
  };
}

function safeCount(fn: (() => unknown[]) | undefined): number {
  try {
    return fn ? (fn() ?? []).length : 0;
  } catch {
    return 0;
  }
}

/** §8 — remote media identity/lifecycle. Read-only. */
export function collectRemoteMediaState(sdh: SdhView | null | undefined): RemoteMediaSummary {
  const none: RemoteMediaSummary = {
    present: false,
    remoteMediaStreamId: null,
    streamTrackCount: 0,
    trackId: null,
    trackKind: null,
    trackMuted: null,
    trackEnabled: null,
    trackReadyState: null,
    source: 'none',
  };
  const stream = sdh?.remoteMediaStream;
  if (stream) {
    let tracks: MediaStreamTrack[] = [];
    try {
      tracks = stream.getTracks?.() ?? [];
    } catch {
      tracks = [];
    }
    const audio = tracks.find((t) => t.kind === 'audio') ?? tracks[0];
    const t = summarizeTrack(audio);
    return {
      present: true,
      remoteMediaStreamId: typeof stream.id === 'string' ? stream.id : null,
      streamTrackCount: tracks.length,
      trackId: t.id,
      trackKind: t.kind,
      trackMuted: t.muted,
      trackEnabled: t.enabled,
      trackReadyState: t.readyState,
      source: 'remoteMediaStream',
    };
  }
  // Fall back to the receiver: `remoteMediaStream` is absent until tracks arrive.
  const pc = asPc(sdh?.peerConnection);
  let receivers: ReceiverView[] = [];
  try {
    receivers = pc?.getReceivers?.() ?? [];
  } catch {
    receivers = [];
  }
  const track = receivers.find((r) => r.track?.kind === 'audio')?.track ?? receivers[0]?.track;
  if (!track) return none;
  const t = summarizeTrack(track);
  return {
    present: true,
    remoteMediaStreamId: null,
    streamTrackCount: 0,
    trackId: t.id,
    trackKind: t.kind,
    trackMuted: t.muted,
    trackEnabled: t.enabled,
    trackReadyState: t.readyState,
    source: 'receiver',
  };
}

/** §9 — observation of the EXISTING production element. Never mutates it. */
export function collectAudioElementState(element: HTMLAudioElement | null | undefined): AudioElementSummary | null {
  if (!element) return null;
  let srcObject: MediaStream | null = null;
  try {
    srcObject = (element.srcObject ?? null) as MediaStream | null;
  } catch {
    srcObject = null;
  }
  let sinkId = '';
  let sinkSupported = false;
  try {
    const withSink = element as HTMLAudioElement & { sinkId?: unknown; setSinkId?: unknown };
    sinkId = asText(withSink.sinkId);
    sinkSupported = typeof withSink.setSinkId === 'function';
  } catch {
    sinkId = '';
    sinkSupported = false;
  }
  let trackIds: string[] = [];
  try {
    trackIds = srcObject ? srcObject.getAudioTracks().map((t) => t.id) : [];
  } catch {
    trackIds = [];
  }
  return {
    present: true,
    srcObjectPresent: !!srcObject,
    srcObjectId: srcObject ? srcObject.id : null,
    srcObjectTrackIds: trackIds,
    paused: element.paused !== false,
    muted: element.muted === true,
    volume: typeof element.volume === 'number' ? element.volume : 0,
    readyState: element.readyState ?? 0,
    networkState: element.networkState ?? 0,
    currentTime: Number.isFinite(element.currentTime) ? Number(element.currentTime.toFixed(2)) : -1,
    sinkId,
    sinkSupported,
  };
}

/**
 * §10 — inbound RTP + selected candidate pair.
 *
 * Missing browser statistics are reported as `null` / `UNAVAILABLE`, never
 * as failure and never as zero.
 */
export async function collectRtpState(pc: unknown): Promise<RtpSummary | null> {
  const view = asPc(pc);
  if (!view || typeof view.getStats !== 'function') return null;
  const base: RtpSummary = {
    present: false,
    ssrc: null,
    codec: null,
    payloadType: null,
    packetsReceived: null,
    bytesReceived: null,
    packetsLost: null,
    jitter: null,
    audioLevel: null,
    totalAudioEnergy: null,
    totalSamplesReceived: null,
    jitterBufferEmittedCount: null,
    concealedSamples: null,
    lastPacketReceivedTimestamp: null,
    timestamp: Date.now(),
    candidatePairState: UNAVAILABLE,
    localCandidateType: UNAVAILABLE,
    remoteCandidateType: UNAVAILABLE,
    rttSeconds: null,
    availableLocalCandidateTypes: [],
  };
  const reports: Record<string, unknown>[] = [];
  try {
    const report = await view.getStats();
    report.forEach((r) => {
      if (r && typeof r === 'object') reports.push(r as Record<string, unknown>);
    });
  } catch {
    return base;
  }

  const codecs = new Map<string, Record<string, unknown>>();
  for (const r of reports) if (r.type === 'codec' && typeof r.id === 'string') codecs.set(r.id, r);

  let pairId: string | null = null;
  for (const r of reports) {
    if (r.type === 'candidate-pair' && r.selected === true && typeof r.id === 'string') pairId = r.id;
  }
  const pair = pairId ? reports.find((r) => r.id === pairId) : undefined;
  const localPair = pair ? reports.find((r) => r.id === pair.localCandidateId) : undefined;
  const remotePair = pair ? reports.find((r) => r.id === pair.remoteCandidateId) : undefined;

  const out: RtpSummary = {
    ...base,
    availableLocalCandidateTypes: [
      ...new Set(
        reports
          .filter((r) => r.type === 'local-candidate')
          .map((r) => String(r.candidateType))
          .filter((t) => t.length > 0),
      ),
    ],
  };
  if (pair) {
    out.candidatePairState = asText(pair.state) || UNAVAILABLE;
    out.rttSeconds = numOrNull(pair.currentRoundTripTime);
  }
  out.localCandidateType = localPair ? asText(localPair.candidateType) || UNAVAILABLE : UNAVAILABLE;
  out.remoteCandidateType = remotePair ? asText(remotePair.candidateType) || UNAVAILABLE : UNAVAILABLE;

  for (const r of reports) {
    if (r.type !== 'inbound-rtp') continue;
    if (r.kind !== 'audio' && r.mediaType !== 'audio') continue;
    const codec = typeof r.codecId === 'string' ? codecs.get(r.codecId) : undefined;
    out.present = true;
    out.ssrc = numOrNull(r.ssrc);
    out.codec = codec ? asText(codec.mimeType) || null : typeof r.codecId === 'string' ? r.codecId : null;
    // RTCStatsReport exposes payloadType as a NUMBER; report it as text.
    out.payloadType =
      codec && codec.payloadType !== undefined && codec.payloadType !== null
        ? String(codec.payloadType)
        : null;
    out.packetsReceived = numOrNull(r.packetsReceived);
    out.bytesReceived = numOrNull(r.bytesReceived);
    out.packetsLost = numOrNull(r.packetsLost);
    out.jitter = numOrNull(r.jitter);
    out.audioLevel = numOrNull(r.audioLevel);
    out.totalAudioEnergy = numOrNull(r.totalAudioEnergy);
    out.totalSamplesReceived = numOrNull(r.totalSamplesReceived);
    out.jitterBufferEmittedCount = numOrNull(r.jitterBufferEmittedCount);
    out.concealedSamples =
      numOrNull(r.concealedSamples) === null && numOrNull(r.silentConcealedSamples) === null
        ? null
        : (numOrNull(r.concealedSamples) ?? 0) + (numOrNull(r.silentConcealedSamples) ?? 0);
    out.lastPacketReceivedTimestamp = numOrNull(r.lastPacketReceivedTimestamp);
    break;
  }
  return out;
}

/** §5 — SIP response facts. Dialog identity reduced to an 8-char suffix. */
export function summarizeSipResponse(response: unknown): SipResponseSummary {
  const empty: SipResponseSummary = {
    statusCode: null,
    reasonPhrase: null,
    cseq: null,
    dialogTagSuffix: null,
  };
  if (!response || typeof response !== 'object') return empty;
  const r = response as {
    statusCode?: unknown;
    reasonPhrase?: unknown;
    message?: { statusCode?: unknown; reasonPhrase?: unknown; getHeader?: (h: string) => string };
    getHeader?: (h: string) => string;
  };
  const message = r.message ?? (response as { message?: unknown }).message as never;
  const statusCode = numOrNull(r.statusCode) ?? numOrNull((message as { statusCode?: unknown })?.statusCode);
  const reasonPhrase =
    asText(r.reasonPhrase) || asText((message as { reasonPhrase?: unknown })?.reasonPhrase) || null;
  const header = (name: string): string | null => {
    try {
      const v = r.getHeader?.(name) ?? (message as { getHeader?: (h: string) => string })?.getHeader?.(name);
      return typeof v === 'string' && v.length > 0 ? v : null;
    } catch {
      return null;
    }
  };
  const callId = header('call-id') ?? header('Call-ID');
  return {
    statusCode,
    reasonPhrase,
    cseq: header('cseq') ?? header('CSeq'),
    dialogTagSuffix: callId ? callId.slice(-8) : null,
  };
}

/** §5 — exact failure classification; never swallow the error kind. */
export function classifyReinviteError(error: unknown): {
  name: string;
  message: string;
  statusCode: number | null;
  kind:
    | 'REQUEST_PENDING'
    | 'SIP_NON_2XX'
    | 'TIMEOUT'
    | 'TRANSPORT'
    | 'INVALID_STATE'
    | 'UNKNOWN';
} {
  const err = error as { name?: unknown; message?: unknown; statusCode?: unknown } | null;
  const name = asText(err?.name) || 'Error';
  const message = scrubDiagnosticText(asText(err?.message) || String(error));
  const statusCode = numOrNull(err?.statusCode) ?? sipRejectionStatus(error);
  if (name === 'RequestPendingError') return { name, message, statusCode, kind: 'REQUEST_PENDING' };
  if (statusCode !== null && statusCode >= 300) return { name, message, statusCode, kind: 'SIP_NON_2XX' };
  if (/invalid session state/i.test(message)) return { name, message, statusCode, kind: 'INVALID_STATE' };
  if (/timed?\s*out|timeout/i.test(message)) return { name, message, statusCode, kind: 'TIMEOUT' };
  if (/transport|websocket|network|connection|unavailable/i.test(message)) {
    return { name, message, statusCode, kind: 'TRANSPORT' };
  }
  return { name, message, statusCode, kind: 'UNKNOWN' };
}

/** Walk an error chain for a SIP status code (mirrors the adapter's helper). */
export function sipRejectionStatus(error: unknown): number | null {
  const seen = new Set<unknown>();
  let current: unknown = error;
  for (let depth = 0; depth < 3 && current && typeof current === 'object'; depth += 1) {
    if (seen.has(current)) break;
    seen.add(current);
    const rec = current as Record<string, unknown>;
    const direct = numOrNull(rec.statusCode) ?? numOrNull(rec.status);
    if (direct !== null && direct >= 100 && direct < 700) return direct;
    const nested = numOrNull((rec.message as { statusCode?: unknown } | undefined)?.statusCode);
    if (nested !== null && nested >= 100 && nested < 700) return nested;
    current = rec.cause ?? rec.response ?? rec.error;
  }
  return null;
}

// ---------------------------------------------------------------------------
// bounded trace store
// ---------------------------------------------------------------------------

export const HOLD_TRACE_LIMIT = 40;
export const HOLD_EVENT_LIMIT = 120;
export const HOLD_TIMELINE_LIMIT = 16;

export interface HoldDiagnostics {
  readonly enabled: boolean;
  begin(operation: HoldOperation, callId: string, holdStateBefore: boolean | null): HoldTrace;
  event(trace: HoldTrace, stage: HoldStage | string, detail?: Record<string, unknown>): void;
  attach(trace: HoldTrace, which: 'before' | 'after', snapshot: Snapshot): void;
  timeline(trace: HoldTrace, snapshot: Snapshot): void;
  complete(trace: HoldTrace, error?: unknown): void;
  list(): HoldTrace[];
  get(transactionId: string): HoldTrace | null;
  latest(): HoldTrace | null;
  clear(): void;
  /** Next transaction id for the given operation (HOLD-001, RESUME-002...). */
  nextTransactionId(operation: HoldOperation): string;
}

/** Diagnostics-disabled singleton: every method is a no-op. */
export const DISABLED_HOLD_DIAGNOSTICS: HoldDiagnostics = {
  enabled: false,
  begin: () => ({ ...emptyTrace('NONE', 'HOLD') }),
  event: () => undefined,
  attach: () => undefined,
  timeline: () => undefined,
  complete: () => undefined,
  list: () => [],
  get: () => null,
  latest: () => null,
  clear: () => undefined,
  nextTransactionId: () => 'DISABLED',
};

function emptyTrace(transactionId: string, operation: HoldOperation): HoldTrace {
  return {
    transactionId,
    operation,
    callId: '',
    startedAt: Date.now(),
    completedAt: null,
    durationMs: null,
    holdStateBefore: null,
    holdStateAfter: null,
    ackObservation: ACK_OBSERVATION,
    events: [],
    before: null,
    after: null,
    timeline: [],
    outgoingSdp: null,
    answerSdp: null,
    preModifierOfferSdp: null,
    responses: [],
    error: null,
    classification: null,
  };
}

/**
 * Bounded, in-memory, non-persisting trace store.
 *
 * Nothing is logged or persisted: the caller reads traces explicitly from the
 * `?diag=1` panel. Capacity is hard-capped so a long call cannot grow it.
 */
export function createHoldDiagnostics(limit = HOLD_TRACE_LIMIT): HoldDiagnostics {
  const traces: HoldTrace[] = [];
  const counters: Record<HoldOperation, number> = { HOLD: 0, RESUME: 0 };

  return {
    enabled: true,
    nextTransactionId(operation) {
      counters[operation] += 1;
      return `${operation}-${String(counters[operation]).padStart(3, '0')}`;
    },
    begin(operation, callId, holdStateBefore) {
      const trace = emptyTrace(this.nextTransactionId(operation), operation);
      trace.callId = callId;
      trace.holdStateBefore = holdStateBefore;
      traces.push(trace);
      while (traces.length > limit) traces.shift();
      return trace;
    },
    event(trace, stage, detail = {}) {
      trace.events.push({ at: Date.now(), stage, detail });
      if (trace.events.length > HOLD_EVENT_LIMIT) trace.events.shift();
    },
    attach(trace, which, snapshot) {
      trace[which] = snapshot;
    },
    timeline(trace, snapshot) {
      trace.timeline.push(snapshot);
      while (trace.timeline.length > HOLD_TIMELINE_LIMIT) trace.timeline.shift();
    },
    complete(trace, error) {
      trace.completedAt = Date.now();
      trace.durationMs = trace.completedAt - trace.startedAt;
      if (error !== undefined) trace.error = classifyReinviteError(error);
      trace.classification = classifyHoldTrace(trace);
    },
    list() {
      return [...traces];
    },
    get(transactionId) {
      return traces.find((t) => t.transactionId === transactionId) ?? null;
    },
    latest() {
      return traces.length > 0 ? traces[traces.length - 1]! : null;
    },
    clear() {
      traces.length = 0;
      counters.HOLD = 0;
      counters.RESUME = 0;
    },
  };
}

// ---------------------------------------------------------------------------
// §11 / §16 classification
// ---------------------------------------------------------------------------

/** Last RTP snapshot at or before `from`, used for post-resume deltas. */
function rtpDeltaOver(
  timeline: Snapshot[],
  from: number,
  key: 'packetsReceived' | 'bytesReceived' | 'audioLevel' | 'totalSamplesReceived',
): number | null {
  const inWindow = timeline.filter((s) => s.at >= from && s.rtp !== null);
  if (inWindow.length < 2) return null;
  const first = inWindow[0]!.rtp!;
  const last = inWindow[inWindow.length - 1]!.rtp!;
  const a = first[key];
  const b = last[key];
  if (typeof a !== 'number' || typeof b !== 'number') return null;
  return b - a;
}

/**
 * §11 — make the failure modes distinguishable. Returns ONE primary
 * classification plus any secondary ones, each with concrete evidence.
 *
 * §11 D is never asserted alone: it is only offered as a candidate and always
 * flagged `requiresHumanAudibilityConfirmation`.
 */
export function classifyHoldTrace(trace: HoldTrace): HoldClassification {
  const evidence: string[] = [];
  const found: HoldClassificationCode[] = [];
  const isResume = trace.operation === 'RESUME';
  const rejected = trace.responses.some((r) => r.statusCode !== null && r.statusCode >= 300);
  const accepted = trace.responses.some((r) => r.statusCode !== null && r.statusCode >= 200 && r.statusCode < 300);
  const finalStatus = [...trace.responses].reverse().find((r) => r.statusCode !== null)?.statusCode ?? null;

  // --- Case G: pending / stalled -----------------------------------------
  if (trace.error?.kind === 'REQUEST_PENDING' || trace.before?.pc.reinvitePending === true) {
    found.push('REINVITE_PENDING_OR_STALLED');
    evidence.push(
      trace.error?.kind === 'REQUEST_PENDING'
        ? `RequestPendingError: ${trace.error.message}`
        : 'Session.pendingReinvite was true when the command started',
    );
  }
  if (trace.completedAt === null) {
    if (!found.includes('REINVITE_PENDING_OR_STALLED')) found.push('REINVITE_PENDING_OR_STALLED');
    evidence.push('COMMAND_COMPLETED never observed');
  }

  // --- Case A: rejected ---------------------------------------------------
  if (rejected || trace.error?.kind === 'SIP_NON_2XX') {
    found.push('SIP_REINVITE_REJECTED');
    evidence.push(
      `final response ${finalStatus ?? 'none'} (reasons: ${trace.responses
        .map((r) => `${r.statusCode ?? '?'}${r.reasonPhrase ? ` ${r.reasonPhrase}` : ''}`)
        .join(', ') || 'none'})`,
    );
    if (trace.error) evidence.push(`error ${trace.error.name}/${trace.error.kind}: ${trace.error.message}`);
  }

  // --- the sticky-modifier fingerprint (SIP.js re-INVITE modifier state) --
  if (trace.before?.pc.holdModifierStillInstalled === true) {
    evidence.push(
      'Session.sessionDescriptionHandlerModifiersReInvite still contained Web.holdModifier when the command started — SIP.js re-applies it to this re-INVITE offer',
    );
  }

  // --- Case B: SDP direction not restored ---------------------------------
  const offerDir = trace.outgoingSdp?.direction ?? null;
  const answerDir = trace.answerSdp?.direction ?? null;
  if (isResume && accepted && !trace.error) {
    if (offerDir !== null && offerDir !== 'sendrecv') {
      found.push('SDP_DIRECTION_NOT_RESTORED');
      evidence.push(`RESUME offer direction=${offerDir} (expected sendrecv)`);
    }
    if (answerDir !== null && (answerDir === 'inactive' || answerDir === 'sendonly')) {
      // Answerer sends nothing towards us => we receive nothing.
      if (!found.includes('SDP_DIRECTION_NOT_RESTORED')) found.push('SDP_DIRECTION_NOT_RESTORED');
      evidence.push(`RESUME answer direction=${answerDir} (remote offers us no inbound media)`);
    }
    const afterTransceivers = trace.after?.pc.transceivers ?? [];
    const audio = afterTransceivers.find((t) => (t.kind ?? '').includes('audio') || t.receiverTrackKind === 'audio');
    if (audio && audio.currentDirection !== null && audio.currentDirection !== 'sendrecv') {
      if (!found.includes('SDP_DIRECTION_NOT_RESTORED')) found.push('SDP_DIRECTION_NOT_RESTORED');
      evidence.push(`transceiver currentDirection=${audio.currentDirection} after resume`);
    }
  }

  // --- Case E: remote track lifecycle ------------------------------------
  const beforeRemote = trace.before?.remote ?? null;
  const afterRemote = trace.after?.remote ?? null;
  if (beforeRemote && afterRemote) {
    if (beforeRemote.trackId !== afterRemote.trackId) {
      found.push('REMOTE_TRACK_LIFECYCLE_CHANGE');
      evidence.push(`remote track id changed ${beforeRemote.trackId ?? 'none'} → ${afterRemote.trackId ?? 'none'}`);
    }
    if (beforeRemote.trackReadyState !== afterRemote.trackReadyState) {
      if (!found.includes('REMOTE_TRACK_LIFECYCLE_CHANGE')) found.push('REMOTE_TRACK_LIFECYCLE_CHANGE');
      evidence.push(`remote track readyState ${beforeRemote.trackReadyState} → ${afterRemote.trackReadyState}`);
    }
    if (beforeRemote.trackMuted !== afterRemote.trackMuted) {
      if (!found.includes('REMOTE_TRACK_LIFECYCLE_CHANGE')) found.push('REMOTE_TRACK_LIFECYCLE_CHANGE');
      evidence.push(`remote track muted ${beforeRemote.trackMuted} → ${afterRemote.trackMuted}`);
    }
    if (beforeRemote.remoteMediaStreamId !== afterRemote.remoteMediaStreamId) {
      evidence.push(
        `remoteMediaStream ${beforeRemote.remoteMediaStreamId ?? 'none'} → ${afterRemote.remoteMediaStreamId ?? 'none'}`,
      );
    }
  }

  // --- Case F: peer connection state -------------------------------------
  const beforePc = trace.before?.pc ?? null;
  const afterPc = trace.after?.pc ?? null;
  if (beforePc && afterPc) {
    if (beforePc.connectionState !== afterPc.connectionState) {
      if (!found.includes('PEER_CONNECTION_STATE_CHANGE')) found.push('PEER_CONNECTION_STATE_CHANGE');
      evidence.push(`connectionState ${beforePc.connectionState} → ${afterPc.connectionState}`);
    }
    if (beforePc.iceConnectionState !== afterPc.iceConnectionState) {
      evidence.push(`iceConnectionState ${beforePc.iceConnectionState} → ${afterPc.iceConnectionState}`);
    }
    if (afterPc.signalingState !== 'stable') {
      if (!found.includes('PEER_CONNECTION_STATE_CHANGE')) found.push('PEER_CONNECTION_STATE_CHANGE');
      evidence.push(`signalingState=${afterPc.signalingState} after the re-INVITE`);
    }
  }

  // --- Case C: RTP stops after a successful resume ------------------------
  if (isResume && !found.some((f) => f === 'SIP_REINVITE_REJECTED' || f === 'SDP_DIRECTION_NOT_RESTORED')) {
    const window = trace.timeline.filter((s) => s.at >= trace.startedAt);
    const dPk = rtpDeltaOver(window, trace.startedAt, 'packetsReceived');
    const dSamples = rtpDeltaOver(window, trace.startedAt, 'totalSamplesReceived');
    const healthy =
      afterPc?.iceConnectionState === 'connected' &&
      (afterRemote?.trackReadyState === 'live' || afterRemote?.present === true);
    if (dPk !== null && dPk <= 0 && healthy) {
      found.push('RTP_MEDIA_PATH_FAILURE_AFTER_RESUME');
      evidence.push(`packetsReceived delta=${dPk} with ice=${afterPc?.iceConnectionState} and a live remote track`);
    } else if (dPk !== null) {
      evidence.push(`packetsReceived delta=${dPk}, totalSamplesReceived delta=${dSamples}`);
    } else {
      evidence.push('no inbound-rtp samples in the post-resume window (RTP evidence UNAVAILABLE)');
    }
  }

  // --- Case D: RTP present, audio inaudible (candidate only) -------------
  const needsHuman = found.length === 0 || found.includes('RTP_MEDIA_PATH_FAILURE_AFTER_RESUME');
  if (needsHuman) {
    const dPk = rtpDeltaOver(trace.timeline.filter((s) => s.at >= trace.startedAt), trace.startedAt, 'packetsReceived');
    const levels = trace.timeline
      .filter((s) => s.at >= trace.startedAt && s.rtp !== null)
      .map((s) => s.rtp!.audioLevel)
      .filter((v): v is number => typeof v === 'number');
    const maxLevel = levels.length > 0 ? Math.max(...levels) : null;
    const el = trace.after?.element ?? null;
    if (dPk !== null && dPk > 0 && (maxLevel === null || maxLevel > 0) && el && !el.paused) {
      found.push('BROWSER_OUTPUT_PATH_SUSPECTED');
      evidence.push(
        `packetsReceived delta=${dPk}, maxAudioLevel=${maxLevel ?? UNAVAILABLE}, element paused=${el.paused} muted=${el.muted} readyState=${el.readyState}`,
      );
    }
  }

  if (found.length === 0) {
    evidence.push(
      `re-INVITE final=${finalStatus ?? 'none'} offer=${offerDir ?? UNAVAILABLE} answer=${answerDir ?? UNAVAILABLE}; no rejected, direction, PC, track or RTP anomaly observed`,
    );
    return {
      primary: 'NO_APP_LAYER_FAILURE_DETECTED',
      secondary: [],
      evidence,
      requiresHumanAudibilityConfirmation: true,
    };
  }

  // Priority: the earliest failing layer explains the rest.
  const order: HoldClassificationCode[] = [
    'REINVITE_PENDING_OR_STALLED',
    'SIP_REINVITE_REJECTED',
    'SDP_DIRECTION_NOT_RESTORED',
    'PEER_CONNECTION_STATE_CHANGE',
    'REMOTE_TRACK_LIFECYCLE_CHANGE',
    'RTP_MEDIA_PATH_FAILURE_AFTER_RESUME',
    'BROWSER_OUTPUT_PATH_SUSPECTED',
  ];
  const primary = order.find((c) => found.includes(c))!;
  return {
    primary,
    secondary: found.filter((c) => c !== primary),
    evidence,
    requiresHumanAudibilityConfirmation: true,
  };
}

/** §15 — the transition table, one row per observed stage. */
export interface TransitionRow {
  stage: string;
  sip: string;
  sdpDirection: string;
  pcState: string;
  remoteTrack: string;
  rtp: string;
  audio: string;
}

/** Renders the §15 table from a completed trace. Never fills unknowns. */
export function buildTransitionTable(trace: HoldTrace): TransitionRow[] {
  const unknown = UNAVAILABLE;
  const row = (
    stage: string,
    snap: Snapshot | null,
    sip: string,
    sdp: string,
    audio: string,
  ): TransitionRow => ({
    stage,
    sip,
    sdpDirection: sdp,
    pcState: snap
      ? `${snap.pc.connectionState ?? unknown}/${snap.pc.iceConnectionState ?? unknown}/${snap.pc.signalingState ?? unknown}`
      : unknown,
    remoteTrack: snap
      ? `${snap.remote.trackId ?? 'none'}:${snap.remote.trackReadyState ?? unknown}:${snap.remote.trackMuted === null ? unknown : snap.remote.trackMuted ? 'muted' : 'unmuted'}`
      : unknown,
    rtp: snap?.rtp?.present
      ? `pk=${snap.rtp.packetsReceived ?? unknown} by=${snap.rtp.bytesReceived ?? unknown} lvl=${snap.rtp.audioLevel ?? unknown} ssrc=${snap.rtp.ssrc ?? unknown}`
      : UNAVAILABLE,
    audio,
  });
  const finalStatus = [...trace.responses].reverse().find((r) => r.statusCode !== null)?.statusCode;
  const rows: TransitionRow[] = [
    row('Before', trace.before, '—', trace.before?.pc.transceivers.find((t) => t.currentDirection)?.currentDirection ?? unknown, trace.before?.element ? describeAudio(trace.before.element) : unknown),
    row(
      `${trace.operation} sent`,
      trace.before,
      `INVITE (${trace.transactionId})`,
      trace.outgoingSdp?.direction ?? unknown,
      trace.before?.element ? describeAudio(trace.before.element) : unknown,
    ),
    row(
      `${trace.operation} response`,
      trace.after,
      finalStatus !== undefined ? String(finalStatus) : trace.responses.length > 0 ? 'no-status' : UNAVAILABLE,
      trace.answerSdp?.direction ?? unknown,
      trace.after?.element ? describeAudio(trace.after.element) : unknown,
    ),
  ];
  for (const snap of trace.timeline) {
    rows.push(row(`+${snap.at - trace.startedAt}ms`, snap, '—', '—', snap.element ? describeAudio(snap.element) : unknown));
  }
  rows.push(
    row('After', trace.after, `ACK=${trace.ackObservation}`, trace.after?.pc.transceivers.find((t) => t.currentDirection)?.currentDirection ?? unknown, trace.after?.element ? describeAudio(trace.after.element) : unknown),
  );
  return rows;
}

function describeAudio(el: AudioElementSummary): string {
  return `srcObject=${el.srcObjectId ?? 'none'} paused=${el.paused} muted=${el.muted} vol=${el.volume} ready=${el.readyState}`;
}

/** Fixed-width render of the §15 table for logs and the ?diag=1 panel. */
export function formatTransitionTable(rows: TransitionRow[]): string {
  const head = ['Stage', 'SIP', 'SDP Direction', 'PC State', 'Remote Track', 'RTP', 'Audio'];
  const body = rows.map((r) => [r.stage, r.sip, r.sdpDirection, r.pcState, r.remoteTrack, r.rtp, r.audio]);
  const all = [head, ...body];
  const widths = head.map((_, c) => Math.max(...all.map((r) => r[c]!.length)));
  return all
    .map((r) => r.map((c, j) => c.padEnd(widths[j]!)).join('  ').trimEnd())
    .map((line, i) => (i === 0 ? `${line}\n${widths.map((w) => '-'.repeat(w)).join('  ')}` : line))
    .join('\n');
}

/** Flat, log-safe rendering of one trace (bounded, already redacted). */
export function formatHoldTrace(trace: HoldTrace): string {
  const lines: string[] = [
    `== ${trace.transactionId} (${trace.operation}) call=${trace.callId} held ${trace.holdStateBefore} → ${trace.holdStateAfter} dur=${trace.durationMs ?? UNAVAILABLE}ms ack=${trace.ackObservation}`,
  ];
  for (const e of trace.events) {
    lines.push(`   ${new Date(e.at).toISOString().slice(11, 23)} ${e.stage} ${JSON.stringify(e.detail)}`);
  }
  if (trace.outgoingSdp) lines.push(`   outgoingSdp=${JSON.stringify(trace.outgoingSdp)}`);
  if (trace.answerSdp) lines.push(`   answerSdp=${JSON.stringify(trace.answerSdp)}`);
  if (trace.error) lines.push(`   error=${JSON.stringify(trace.error)}`);
  if (trace.classification) {
    lines.push(`   classification=${trace.classification.primary} secondary=[${trace.classification.secondary.join(',')}]`);
    for (const e of trace.classification.evidence) lines.push(`     - ${e}`);
  }
  return lines.join('\n');
}