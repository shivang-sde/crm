/**
 * VOICE-07 FIX-9 — browser audio-output diagnostics (development/test only).
 *
 * Pure, dependency-free helpers over DOM/WebRTC state. No SIP, no secrets,
 * no persistence, no network. UI layers (popup ?diag=1 panel, test harness)
 * call these to render human-readable audio-path state for manual
 * acceptance. Automated tests assert structure/behavior, never audibility.
 */

export interface AudioTrackReport {
  kind: string;
  live: boolean;
  enabled: boolean;
  muted: boolean;
}

export interface AudioElementReport {
  present: boolean;
  muted: boolean;
  volume: number;
  paused: boolean;
  readyState: number;
  networkState: number;
  currentTime: number;
  hasStream: boolean;
  audioTracks: AudioTrackReport[];
  sinkSupported: boolean;
  sinkId: string;
}

/** `setSinkId` exists (Chrome/Edge); Firefox/Safari report unsupported. */
export function isSetSinkIdSupported(element: HTMLAudioElement): boolean {
  try {
    return typeof (element as HTMLAudioElement & { setSinkId?: unknown }).setSinkId === 'function';
  } catch {
    return false;
  }
}

function trackReport(track: MediaStreamTrack): AudioTrackReport {
  return {
    kind: track.kind ?? '?',
    live: track.readyState === 'live',
    enabled: track.enabled !== false,
    muted: track.muted === true,
  };
}

/** Element state only — never track labels, never stream contents. */
export function describeAudioElement(element: HTMLAudioElement | null | undefined): AudioElementReport {
  if (!element) {
    return {
      present: false, muted: true, volume: 0, paused: true, readyState: 0,
      networkState: 0, currentTime: 0, hasStream: false, audioTracks: [],
      sinkSupported: false, sinkId: '',
    };
  }
  let tracks: AudioTrackReport[] = [];
  let hasStream = false;
  try {
    const stream = element.srcObject as MediaStream | null;
    hasStream = !!stream;
    tracks = stream ? stream.getAudioTracks().map(trackReport) : [];
  } catch {
    tracks = [];
  }
  let sinkId = '';
  try {
    sinkId = (element as HTMLAudioElement & { sinkId?: unknown }).sinkId as string ?? '';
  } catch {
    sinkId = '';
  }
  return {
    present: true,
    muted: element.muted === true,
    volume: typeof element.volume === 'number' ? element.volume : 0,
    paused: element.paused !== false,
    readyState: element.readyState ?? 0,
    networkState: element.networkState ?? 0,
    currentTime: element.currentTime ?? 0,
    hasStream,
    audioTracks: tracks,
    sinkSupported: isSetSinkIdSupported(element),
    sinkId: typeof sinkId === 'string' ? sinkId : '',
  };
}

/** Remote-stream summary: counts and flags only, no labels/contents. */
export function describeRemoteStream(stream: MediaStream | null | undefined): {
  present: boolean;
  audioTracks: AudioTrackReport[];
} {
  if (!stream) return { present: false, audioTracks: [] };
  let tracks: AudioTrackReport[] = [];
  try {
    tracks = stream.getAudioTracks().map(trackReport);
  } catch {
    tracks = [];
  }
  return { present: true, audioTracks: tracks };
}

export interface OutputDeviceReport {
  supported: boolean;
  /**
   * Transient device entries for the dev-only sink selector. IDs live only
   * in component memory for setSinkId() — never persisted, never logged.
   */
  audioOutputs: { id: string; labelPresent: boolean }[];
  error: string | null;
}

/** Enumerate audioOUTPUT devices. Labels only flagged present/absent (may name hardware). */
export async function listAudioOutputs(): Promise<OutputDeviceReport> {
  try {
    const mediaDevices = navigator?.mediaDevices;
    if (!mediaDevices || typeof mediaDevices.enumerateDevices !== 'function') {
      return { supported: false, audioOutputs: [], error: 'enumerateDevices unsupported' };
    }
    const devices = await mediaDevices.enumerateDevices();
    return {
      supported: true,
      audioOutputs: devices
        .filter((d) => d.kind === 'audiooutput')
        .map((d) => ({ id: d.deviceId ?? '', labelPresent: !!(d.label && d.label.length > 0) })),
      error: null,
    };
  } catch (error) {
    return { supported: false, audioOutputs: [], error: error instanceof Error ? error.message : 'enumerate failed' };
  }
}

/** RMS energy of float time-domain samples (analyser helper, pure math). */
export function rmsEnergy(samples: ArrayLike<number>): number {
  if (!samples || samples.length === 0) return 0;
  let sum = 0;
  for (let i = 0; i < samples.length; i += 1) {
    const v = samples[i] ?? 0;
    sum += v * v;
  }
  return Math.sqrt(sum / samples.length);
}

export interface CodecEntry {
  mimeType: string;
  clockRate: string;
  channels: string;
  payloadType: string;
  sdpFmtpLine: string;
}

export interface InboundAudioSummary {
  packetsReceived: number;
  bytesReceived: number;
  packetsLost: number;
  jitter: number | null;
  jitterBufferDelay: number | null;
  concealedSamples: number;
  totalSamplesReceived: number;
  framesDecoded: number;
  codec: string | null;
}

function asNum(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function asOptNum(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? (value as number) : null;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : null;
}

/** Codec table from RTCStatsReport entries (pure; no secrets in codec stats). */
export function collectCodecTable(reports: Iterable<unknown>): Record<string, CodecEntry> {
  const table: Record<string, CodecEntry> = {};
  for (const report of reports) {
    const r = asRecord(report);
    if (!r || r.type !== 'codec' || typeof r.id !== 'string') continue;
    table[r.id as string] = {
      mimeType: String(r.mimeType ?? '?'),
      clockRate: String(r.clockRate ?? '?'),
      channels: String((r as { channels?: unknown }).channels ?? '?'),
      payloadType: String((r as { payloadType?: unknown }).payloadType ?? '?'),
      sdpFmtpLine: String((r as { sdpFmtpLine?: unknown }).sdpFmtpLine ?? ''),
    };
  }
  return table;
}

/** First audio inbound-rtp summary + codec display (pure). */
export function summarizeInboundAudio(
  reports: Iterable<unknown>,
  codecs: Record<string, CodecEntry>,
): InboundAudioSummary | null {
  for (const report of reports) {
    const r = asRecord(report);
    if (!r || r.type !== 'inbound-rtp') continue;
    if (r.kind !== 'audio' && (r as { mediaType?: unknown }).mediaType !== 'audio') continue;
    return {
      packetsReceived: asNum(r.packetsReceived),
      bytesReceived: asNum(r.bytesReceived),
      packetsLost: asNum(r.packetsLost),
      jitter: asOptNum(r.jitter),
      jitterBufferDelay: asOptNum((r as { jitterBufferDelay?: unknown }).jitterBufferDelay),
      concealedSamples:
        asNum((r as { concealedSamples?: unknown }).concealedSamples) +
        asNum((r as { silentConcealedSamples?: unknown }).silentConcealedSamples),
      totalSamplesReceived: asNum((r as { totalSamplesReceived?: unknown }).totalSamplesReceived),
      framesDecoded: asNum((r as { framesDecoded?: unknown }).framesDecoded),
      codec: typeof r.codecId === 'string' ? (codecs[r.codecId]?.mimeType ?? r.codecId) : null,
    };
  }
  return null;
}

/** Allowlisted raw inbound-rtp fields for forensics (numbers/bools/short strings only). */
export const INBOUND_RAW_FIELDS = [
  'ssrc', 'kind', 'transportId', 'codecId', 'trackIdentifier',
  'packetsReceived', 'packetsLost', 'bytesReceived', 'headerBytesReceived',
  'lastPacketReceivedTimestamp', 'jitter', 'packetsDiscarded', 'packetsRepaired',
  'fecPacketsReceived', 'fecPacketsDiscarded',
  'jitterBufferDelay', 'jitterBufferTargetDelay', 'jitterBufferEmittedCount',
  'jitterBufferMinimumDelay', 'totalSamplesReceived',
  'concealedSamples', 'silentConcealedSamples', 'concealmentEvents',
  'insertedSamplesForDeceleration', 'removedSamplesForAcceleration',
  'framesDecoded', 'framesDropped', 'audioLevel', 'totalAudioEnergy',
  'retransmittedPacketsReceived', 'retransmittedBytesReceived',
  'nackCount', 'pliCount', 'firCount',
];

export function extractInboundRaw(reports: Iterable<unknown>): Record<string, number | string | boolean | null> | null {
  for (const report of reports) {
    const r = asRecord(report);
    if (!r || r.type !== 'inbound-rtp') continue;
    if (r.kind !== 'audio' && (r as { mediaType?: unknown }).mediaType !== 'audio') continue;
    const raw: Record<string, number | string | boolean | null> = {};
    for (const field of INBOUND_RAW_FIELDS) {
      const v = (r as Record<string, unknown>)[field];
      if (typeof v === 'number' && Number.isFinite(v)) raw[field] = v;
      else if (typeof v === 'string') raw[field] = v.slice(0, 80);
      else if (typeof v === 'boolean') raw[field] = v;
    }
    return raw;
  }
  return null;
}

export interface StatsDelta {
  packets: number;
  bytes: number;
  samples: number;
  concealed: number;
  emitted: number;
}

/** Deltas between two snapshots (missing fields count as zero). */
export function computeStatsDelta(
  before: Record<string, number | string | boolean | null> | null,
  after: Record<string, number | string | boolean | null> | null,
): StatsDelta {
  const num = (r: Record<string, number | string | boolean | null> | null, k: string): number =>
    r && typeof r[k] === 'number' ? (r[k] as number) : 0;
  return {
    packets: num(after, 'packetsReceived') - num(before, 'packetsReceived'),
    bytes: num(after, 'bytesReceived') - num(before, 'bytesReceived'),
    samples: num(after, 'totalSamplesReceived') - num(before, 'totalSamplesReceived'),
    concealed: num(after, 'concealedSamples') + num(after, 'silentConcealedSamples')
      - num(before, 'concealedSamples') - num(before, 'silentConcealedSamples'),
    emitted: num(after, 'jitterBufferEmittedCount') - num(before, 'jitterBufferEmittedCount'),
  };
}

/** Layer separation: RTP flow vs sample growth vs concealment vs audible energy. */
export function classifyDecodeEvidence(input: {
  packetsDelta: number;
  samplesDelta: number;
  concealedDelta: number;
  rmsMax: number;
  energyThreshold?: number;
}): { rtpFlowing: boolean; samplesGrowing: boolean; concealing: boolean; audibleEnergy: boolean } {
  const threshold = input.energyThreshold ?? 0.005;
  return {
    rtpFlowing: input.packetsDelta > 0,
    samplesGrowing: input.samplesDelta > 0,
    concealing: input.concealedDelta > 0,
    audibleEnergy: input.rmsMax > threshold,
  };
}

/** Packet-loss percentage 0–100 (received+lost denominator; 0 when empty/unknown). */
export function packetLossPercent(packetsReceived: number, packetsLost: number): number {
  if (typeof packetsReceived !== 'number' || !Number.isFinite(packetsReceived) || packetsReceived < 0) return 0;
  const lost = typeof packetsLost === 'number' && Number.isFinite(packetsLost) && packetsLost > 0 ? packetsLost : 0;
  if (packetsReceived + lost <= 0) return 0;
  return (lost / (packetsReceived + lost)) * 100;
}

/** Concealed share of received samples 0–1 (0 when empty). */
export function concealmentRatio(totalSamplesReceived: number, concealedSamples: number): number {
  const total = typeof totalSamplesReceived === 'number' && Number.isFinite(totalSamplesReceived) ? totalSamplesReceived : 0;
  const concealed = typeof concealedSamples === 'number' && Number.isFinite(concealedSamples) ? concealedSamples : 0;
  if (total <= 0) return 0;
  return Math.min(1, Math.max(0, concealed / total));
}

/**
 * Concealed share of EMITTED (played-out) samples 0–1. Emitted is the honest
 * denominator for "what fraction of heard audio was concealed", because the
 * jitter buffer emits received + concealed samples together.
 */
export function concealmentShareEmitted(concealedSamples: number, emittedSamples: number): number {
  const concealed = typeof concealedSamples === 'number' && Number.isFinite(concealedSamples) ? concealedSamples : 0;
  const emitted = typeof emittedSamples === 'number' && Number.isFinite(emittedSamples) ? emittedSamples : 0;
  if (emitted <= 0) return 0;
  return Math.min(1, Math.max(0, concealed / emitted));
}

/** SSRC transition history (capped); detects source switches mid-call. */
export interface SsrcSample {
  ssrc: string;
  at: number;
}

export function trackSsrcHistory(history: SsrcSample[], ssrc: string | null | undefined, at: number, cap = 20): SsrcSample[] {
  const next = history.slice(-Math.max(1, cap - 1));
  const key = typeof ssrc === 'string' && ssrc.length > 0 ? ssrc : '';
  const last = next[next.length - 1];
  if (!last || last.ssrc !== key) next.push({ ssrc: key, at });
  return next.slice(-cap);
}

export function countSsrcChanges(history: SsrcSample[]): number {
  let changes = 0;
  for (let i = 1; i < history.length; i += 1) {
    if (history[i] && history[i - 1] && history[i]!.ssrc !== history[i - 1]!.ssrc) changes += 1;
  }
  return changes;
}

export type DeliveryVerdict = 'HEALTHY' | 'STARVED' | 'LOSSY' | 'CONCEALING';

/**
 * Delivery classification from one interval's deltas. Priority order is
 * deliberate and documented: STARVED (nothing arrives) outranks LOSSY
 * (arrives damaged), which outranks CONCEALING (jitter buffer fills gaps).
 */
export function analyzeDelivery(input: {
  packetsDelta: number;
  lostDelta: number;
  samplesDelta: number;
  concealedDelta: number;
  emittedDelta: number;
  lossThresholdPct?: number;
}): { lossPct: number; concealmentRatio: number; emittedVsReceived: number; verdict: DeliveryVerdict } {
  const lossPct = packetLossPercent(input.packetsDelta, input.lostDelta);
  const concealmentRatio = input.samplesDelta > 0 ? Math.min(1, Math.max(0, input.concealedDelta / input.samplesDelta)) : 0;
  const emittedVsReceived = input.samplesDelta > 0 ? input.emittedDelta / input.samplesDelta : 0;
  const lossThreshold = input.lossThresholdPct ?? 5;
  let verdict: DeliveryVerdict = 'HEALTHY';
  if (input.packetsDelta <= 0) verdict = 'STARVED';
  else if (lossPct >= lossThreshold) verdict = 'LOSSY';
  else if (input.concealedDelta > 0 && emittedVsReceived > 1.5) verdict = 'CONCEALING';
  return { lossPct, concealmentRatio, emittedVsReceived, verdict };
}

/** Candidate-pair sanitization: type/protocol/ports only, never IPs. */
export function sanitizeCandidatePair(pair: {
  state?: unknown;
  nominated?: unknown;
  localType?: unknown;
  remoteType?: unknown;
  localProtocol?: unknown;
  remoteProtocol?: unknown;
  rtt?: unknown;
  bytesReceived?: unknown;
  bytesSent?: unknown;
} | null | undefined): Record<string, string | number | boolean> {
  if (!pair || typeof pair !== 'object') return {};
  const out: Record<string, string | number | boolean> = {};
  if (typeof pair.state === 'string') out.state = pair.state;
  if (typeof pair.nominated === 'boolean') out.nominated = pair.nominated;
  if (typeof pair.localType === 'string') out.localType = pair.localType;
  if (typeof pair.remoteType === 'string') out.remoteType = pair.remoteType;
  if (typeof pair.localProtocol === 'string') out.localProtocol = pair.localProtocol;
  if (typeof pair.remoteProtocol === 'string') out.remoteProtocol = pair.remoteProtocol;
  if (typeof pair.rtt === 'number' && Number.isFinite(pair.rtt)) out.rtt = pair.rtt;
  if (typeof pair.bytesReceived === 'number' && Number.isFinite(pair.bytesReceived)) out.bytesReceived = pair.bytesReceived;
  if (typeof pair.bytesSent === 'number' && Number.isFinite(pair.bytesSent)) out.bytesSent = pair.bytesSent;
  return out;
}

/**
 * Sanitize SDP for diagnostics: keeps negotiation structure (m=/rtpmap/
 * fmtp/direction/mid/ssrc/msid), masks connection addresses, drops origin
 * and credential-bearing lines. Never logs passwords/tokens.
 */
export function sanitizeSdp(sdp: string): string {
  if (typeof sdp !== 'string') return '';
  return sdp
    .split('\r\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .filter((line) => !line.startsWith('o='))
    .map((line) => {
      if (line.startsWith('c=')) return 'c=<redacted>';
      if (line.startsWith('a=rtcp:')) return 'a=rtcp: <redacted>';
      if (/^a=(ice-ufrag|ice-pwd|fingerprint):/.test(line)) return line.split(':')[0] + ':<present>';
      if (line.startsWith('a=candidate:')) {
        // a=candidate:<foundation> <comp> <proto> <prio> <ip> <port> typ <type> ...
        const parts = line.split(' ');
        if (parts.length >= 8) {
          return [parts[0], parts[1], parts[2], parts[3], '<ip>', '<port>', parts[6], parts[7]].join(' ');
        }
        return 'a=candidate:<redacted>';
      }
      return line;
    })
    .join('\r\n');
}
