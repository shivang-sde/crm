'use client';

/**
 * VOICE-07 FIX-9/FIX-10 — popup-local audio output diagnostics (development only).
 *
 * Rendered ONLY when the popup URL has ?diag=1. Never part of the normal
 * UI. One shared AudioContext feeds BOTH the synthetic tone and the remote
 * WebRTC stream (same destination), so audibility results are comparable.
 * Only one source branch is ever active. No SIP changes, no secrets, no
 * persistence, no backend calls. Human checkboxes are local-only acceptance
 * signals reported back by the operator.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  concealmentRatio,
  concealmentShareEmitted,
  describeAudioElement,
  listAudioOutputs,
  packetLossPercent,
  rmsEnergy,
  type AudioElementReport,
} from '@/lib/voice/audio-diagnostics';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import {
  UNAVAILABLE,
  buildTransitionTable,
  formatTransitionTable,
  type HoldTrace,
} from '@/lib/voice/hold-diagnostics';

type MediaDiag = Awaited<ReturnType<import('@/lib/voice/popup/host').SoftphoneHost['getAudioDiagnostics']>>['media'];

function fmt(value: unknown): string {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'boolean') return value ? 'yes' : 'no';
  return String(value);
}

interface WindowStats {
  peak: number;
  min: number;
  max: number;
  zeroCrossings: number;
  rms: number;
  nonZeroFrames: number;
  frames: number;
  pctAbove: string;
  firstAt: string;
  lastAt: string;
}

const SILENCE_THRESHOLD = 0.005;

export function SoftphoneAudioDiagnostics({ getHost }: {
  getHost: () => import('@/lib/voice/popup/host').SoftphoneHost;
}) {
  const [element, setElement] = useState<AudioElementReport | null>(null);
  const [media, setMedia] = useState<MediaDiag>(null);
  const [outputs, setOutputs] = useState<{ id: string; labelPresent: boolean }[]>([]);
  const [outputsSupported, setOutputsSupported] = useState<boolean | null>(null);
  const [sinkSupported, setSinkSupported] = useState<boolean | null>(null);
  const [sinkResult, setSinkResult] = useState<string>('');
  const [ctxInfo, setCtxInfo] = useState<string>('no shared context yet');
  const [toneResult, setToneResult] = useState<string>('');
  const [elementToneResult, setElementToneResult] = useState<string>('');
  const [gestureResult, setGestureResult] = useState<string>('');
  const [remoteResult, setRemoteResult] = useState<string>('');
  const [htmlAudioResult, setHtmlAudioResult] = useState<string>('');
  const [remoteGain, setRemoteGain] = useState('1');
  const [cloneResult, setCloneResult] = useState<string>('');
  const [eventsLog, setEventsLog] = useState<string[]>([]);
  const [holdTraces, setHoldTraces] = useState<HoldTrace[]>([]);
  const [heardTone, setHeardTone] = useState(false);
  const [heardMoh, setHeardMoh] = useState(false);
  const [heardAfterResume, setHeardAfterResume] = useState(false);
  const [hearRemote, setHearRemote] = useState(false);
  const [hearEarbuds, setHearEarbuds] = useState(false);
  const [hearSpeakers, setHearSpeakers] = useState(false);
  const sharedCtxRef = useRef<AudioContext | null>(null);
  const activeNodesRef = useRef<{ stop: () => void; disconnect: () => void } | null>(null);
  const eventsBoundRef = useRef<HTMLAudioElement | null>(null);

  const refresh = useCallback(async () => {
    try {
      const diag = await getHost().getAudioDiagnostics();
      setElement(diag.element);
      setMedia(diag.media);
      setSinkSupported(diag.element.sinkSupported);
    } catch {
      /* best-effort panel */
    }
    try {
      // FIX-15: empty unless ?diag=1 enabled hold/resume diagnostics.
      setHoldTraces(getHost().getHoldDiagnostics());
    } catch {
      /* best-effort panel */
    }
    try {
      const list = await listAudioOutputs();
      setOutputsSupported(list.supported);
      setOutputs(list.audioOutputs);
    } catch {
      /* best-effort panel */
    }
  }, [getHost]);

  useEffect(() => {
    void refresh();
    const timer = setInterval(() => void refresh(), 2000);
    return () => clearInterval(timer);
  }, [refresh]);

  // Dev-only media-event instrumentation on the popup audio element.
  useEffect(() => {
    let el: HTMLAudioElement | null = null;
    try {
      el = document.querySelector('audio');
    } catch {
      el = null;
    }
    if (!el || eventsBoundRef.current === el) return;
    eventsBoundRef.current = el;
    const names = ['loadedmetadata', 'canplay', 'canplaythrough', 'play', 'playing', 'pause', 'waiting', 'stalled', 'suspend', 'ended', 'error', 'mute', 'unmute', 'timeupdate'] as const;
    const offs: (() => void)[] = [];
    let lastTimeUpdate = 0;
    for (const name of names) {
      const handler = () => {
        // timeupdate fires ~4Hz: sample it at most once per 2s to avoid log churn.
        if (name === 'timeupdate') {
          const now = Date.now();
          if (now - lastTimeUpdate < 2000) return;
          lastTimeUpdate = now;
        }
        const line = `${new Date().toISOString().slice(17, 23)} ${name} paused=${el?.paused} ready=${el?.readyState} t=${Number(el?.currentTime ?? 0).toFixed(1)}`;
        setEventsLog((prev) => [...prev.slice(-11), line]);
      };
      el.addEventListener(name, handler);
      offs.push(() => el?.removeEventListener(name, handler));
    }
    return () => {
      offs.forEach((off) => off());
      if (eventsBoundRef.current === el) eventsBoundRef.current = null;
    };
  }, []);

  const popupAudio = useCallback((): HTMLAudioElement | null => {
    try {
      return document.querySelector('audio');
    } catch {
      return null;
    }
  }, []);

  /** The ONE shared context for every diagnostic branch (§8). */
  const sharedContext = useCallback(async (): Promise<AudioContext | null> => {
    try {
      if (sharedCtxRef.current) {
        await sharedCtxRef.current.resume().catch(() => undefined);
        return sharedCtxRef.current;
      }
      const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!Ctor) return null;
      const ctx = new Ctor();
      sharedCtxRef.current = ctx;
      await ctx.resume().catch(() => undefined);
      return ctx;
    } catch {
      return null;
    }
  }, []);

  const stopActiveBranch = useCallback(() => {
    try {
      activeNodesRef.current?.stop();
    } catch { /* noop */ }
    try {
      activeNodesRef.current?.disconnect();
    } catch { /* noop */ }
    activeNodesRef.current = null;
  }, []);

  useEffect(() => () => {
    stopActiveBranch();
    try {
      void sharedCtxRef.current?.close().catch(() => undefined);
    } catch { /* noop */ }
    sharedCtxRef.current = null;
  }, [stopActiveBranch]);

  /** Sample an analyser for windowMs, reporting speech-like energy stats. */
  const sampleAnalyser = useCallback(async (
    analyser: AnalyserNode,
    windowMs: number,
    onTrackFlags?: () => { readyState: string; enabled: boolean; muted: boolean } | null,
  ): Promise<WindowStats> => {
    const buf = new Float32Array(analyser.fftSize);
    let peak = 0;
    let min = 0;
    let max = 0;
    let zeroCrossings = 0;
    let sum = 0;
    let count = 0;
    let nonZero = 0;
    let firstAt: string | null = null;
    let lastAt: string | null = null;
    let flags = '';
    let prev = 0;
    let hasPrev = false;
    const deadline = Date.now() + windowMs;
    while (Date.now() < deadline) {
      analyser.getFloatTimeDomainData(buf);
      const windowed: number[] = [];
      for (let i = 0; i < buf.length; i += 4) windowed.push(buf[i] ?? 0);
      let framePeak = 0;
      let frameMin = 0;
      let frameMax = 0;
      let frameZc = 0;
      let framePrev = hasPrev ? prev : 0;
      for (const v of windowed) {
        const a = Math.abs(v);
        if (a > framePeak) framePeak = a;
        if (v < frameMin) frameMin = v;
        if (v > frameMax) frameMax = v;
        if (hasPrev && (framePrev <= 0) !== (v <= 0)) frameZc += 1;
        framePrev = v;
        hasPrev = true;
      }
      prev = framePrev;
      const windowRms = rmsEnergy(windowed);
      if (framePeak > peak) peak = framePeak;
      if (frameMin < min) min = frameMin;
      if (frameMax > max) max = frameMax;
      zeroCrossings += frameZc;
      sum += windowRms * windowRms;
      count += 1;
      if (framePeak > SILENCE_THRESHOLD) {
        nonZero += 1;
        const stamp = new Date().toISOString().slice(17, 23);
        if (!firstAt) firstAt = stamp;
        lastAt = stamp;
      }
      if (onTrackFlags) {
        const f = onTrackFlags();
        if (f) flags = ` track=${f.readyState}/${f.enabled ? 'en' : 'dis'}/${f.muted ? 'mut' : 'unmut'}`;
      }
      await new Promise((resolve) => setTimeout(resolve, 250));
    }
    void flags;
    return {
      peak,
      min,
      max,
      zeroCrossings,
      rms: count > 0 ? Math.sqrt(sum / count) : 0,
      nonZeroFrames: nonZero,
      frames: count,
      pctAbove: count > 0 ? `${Math.round((nonZero / count) * 100)}%` : '0%',
      firstAt: firstAt ?? '—',
      lastAt: lastAt ?? '—',
    };
  }, []);

  const reportCtx = useCallback((ctx: AudioContext | null): string => {
    if (!ctx) return 'no context';
    const dest = ctx.destination as AudioDestinationNode & { maxChannelCount?: number };
    return `state=${ctx.state} rate=${ctx.sampleRate} ch=${ctx.destination.channelCount} maxCh=${dest.maxChannelCount ?? '?'}`;
  }, []);

  /** CONTROL A — synthetic tone through the SHARED destination. */
  const playSharedTone = useCallback(async () => {
    setToneResult('running… (listen for 880Hz tone)');
    stopActiveBranch();
    try {
      const ctx = await sharedContext();
      if (!ctx) {
        setToneResult('AudioContext unsupported');
        return;
      }
      setCtxInfo(`shared ctx: ${reportCtx(ctx)}`);
      const osc = ctx.createOscillator();
      osc.frequency.value = 880;
      const gain = ctx.createGain();
      gain.gain.value = 1;
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 2048;
      osc.connect(gain);
      gain.connect(analyser);
      analyser.connect(ctx.destination);
      const stop = () => { try { osc.stop(); } catch { /* noop */ } };
      const disconnect = () => { try { osc.disconnect(); gain.disconnect(); analyser.disconnect(); } catch { /* noop */ } };
      activeNodesRef.current = { stop, disconnect };
      osc.start();
      const stats = await sampleAnalyser(analyser, 3000);
      stopActiveBranch();
      setToneResult(`tone done: peak=${stats.peak.toFixed(3)} rms=${stats.rms.toFixed(5)} ctx=${ctx.state}. Did you HEAR it? Tick below.`);
    } catch (err) {
      setToneResult(`tone failed: ${err instanceof Error ? err.message : 'unknown'}`);
    }
  }, [sharedContext, stopActiveBranch, sampleAnalyser, reportCtx]);

  /** Element-path tone (existing behavior): same <audio> as remote media. */
  const playElementTone = useCallback(async () => {
    setElementToneResult('running…');
    let restoreSrc: MediaStream | null = null;
    try {
      const el = popupAudio();
      if (!el) {
        setElementToneResult('no audio element');
        return;
      }
      const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!Ctor) {
        setElementToneResult('AudioContext unsupported');
        return;
      }
      const ctx = new Ctor();
      await ctx.resume().catch(() => undefined);
      restoreSrc = (el.srcObject ?? null) as MediaStream | null;
      const dest = ctx.createMediaStreamDestination();
      const osc = ctx.createOscillator();
      osc.frequency.value = 880;
      const gain = ctx.createGain();
      gain.gain.value = 0.2;
      osc.connect(gain);
      gain.connect(dest);
      el.srcObject = dest.stream;
      el.muted = false;
      await Promise.race([
        el.play(),
        new Promise((_, reject) => setTimeout(() => reject(new Error('play() timed out')), 8000)),
      ]);
      osc.start();
      await new Promise((resolve) => setTimeout(resolve, 1200));
      try { osc.stop(); } catch { /* noop */ }
      try {
        if (restoreSrc !== undefined) el.srcObject = restoreSrc;
      } catch { /* noop */ }
      await ctx.close().catch(() => undefined);
      setElementToneResult('element tone done (remote stream restored). Did you HEAR it?');
    } catch (err) {
      setElementToneResult(`element tone failed: ${err instanceof Error ? err.message : 'unknown'}`);
    }
  }, [popupAudio]);

  /** CONTROL C — remote via the HTMLAudio ELEMENT path (production path). */
  const playRemoteViaElement = useCallback(async () => {
    setHtmlAudioResult('checking…');
    try {
      const el = popupAudio();
      if (!el) {
        setHtmlAudioResult('no audio element');
        return;
      }
      const stream = (el.srcObject ?? null) as MediaStream | null;
      const tracks = stream ? stream.getAudioTracks().length : 0;
      if (!stream || tracks === 0) {
        setHtmlAudioResult('element has no remote stream (nothing to play)');
        return;
      }
      await Promise.race([
        el.play(),
        new Promise((_, reject) => setTimeout(() => reject(new Error('play() timed out')), 8000)),
      ]);
      setHtmlAudioResult(
        `element playing remote stream: paused=${el.paused} muted=${el.muted} vol=${el.volume} ` +
        `ready=${el.readyState} t=${el.currentTime.toFixed(1)} sink=${(el as HTMLAudioElement & { sinkId?: unknown }).sinkId || 'default'}. Did you HEAR the caller?`,
      );
    } catch (err) {
      setHtmlAudioResult(`element play failed: ${err instanceof Error ? err.message : 'unknown'}`);
    }
  }, [popupAudio]);

  const enableAudio = useCallback(async () => {    setGestureResult('running…');
    try {
      const el = popupAudio();
      if (!el) {
        setGestureResult('no audio element');
        return;
      }
      await Promise.race([
        el.play(),
        new Promise((_, reject) => setTimeout(() => reject(new Error('play() timed out with no media loaded')), 8000)),
      ]);
      setGestureResult(`play() resolved, paused=${el.paused}`);
    } catch (err) {
      setGestureResult(`play() rejected: ${err instanceof Error ? err.message : 'unknown'}`);
    }
  }, [popupAudio]);

  /** CONTROL B — remote stream through the SHARED destination + gain sweep. */
  const measureRemote = useCallback(async (opts?: { clone?: boolean; gain?: number; windowMs?: number }) => {
    const useClone = opts?.clone === true;
    const gainValue = opts?.gain ?? Number(remoteGain) ?? 1;
    const windowMs = opts?.windowMs ?? 8000;
    setRemoteResult(`measuring ${windowMs / 1000}s… (remote party: SPEAK CONTINUOUSLY)`);
    stopActiveBranch();
    try {
      const el = popupAudio();
      const stream = (el?.srcObject ?? null) as MediaStream | null;
      if (!stream || stream.getAudioTracks().length === 0) {
        setRemoteResult('no remote stream on the element');
        return;
      }
      const ctx = await sharedContext();
      if (!ctx) {
        setRemoteResult('AudioContext unsupported');
        return;
      }
      setCtxInfo(`shared ctx: ${reportCtx(ctx)}`);
      const sourceStream = useClone ? stream.clone() : stream;
      const track = sourceStream.getAudioTracks()[0];
      if (!track) {
        setRemoteResult('no audio track');
        return;
      }
      const settings = typeof track.getSettings === 'function' ? track.getSettings() : {};
      const src = ctx.createMediaStreamSource(sourceStream);
      const gain = ctx.createGain();
      gain.gain.value = gainValue;
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 2048;
      src.connect(gain);
      gain.connect(analyser);
      analyser.connect(ctx.destination);
      activeNodesRef.current = {
        stop: () => undefined,
        disconnect: () => {
          try { src.disconnect(); gain.disconnect(); analyser.disconnect(); } catch { /* noop */ }
          try { sourceStream.getTracks().forEach((t) => { if (useClone) t.stop(); }); } catch { /* noop */ }
        },
      };
      const stats = await sampleAnalyser(analyser, windowMs, () => ({
        readyState: track.readyState, enabled: track.enabled, muted: track.muted,
      }));
      stopActiveBranch();
      const constraints = (() => {
        try {
          return typeof track.getConstraints === 'function' ? track.getConstraints() : {};
        } catch {
          return {};
        }
      })();
      const capabilities = (() => {
        try {
          return typeof track.getCapabilities === 'function' ? track.getCapabilities() : {};
        } catch {
          return {};
        }
      })();
      const ch = (settings as { channelCount?: unknown }).channelCount;
      const sr = (settings as { sampleRate?: unknown }).sampleRate;
      const verdict = stats.nonZeroFrames === 0
        ? 'NO REMOTE SPEECH ENERGY DETECTED DURING WINDOW'
        : `signal present (${stats.pctAbove} frames above threshold)`;
      setRemoteResult(
        `${useClone ? 'clone' : 'original'} gain=${gainValue}: peak=${stats.peak.toFixed(4)} min=${stats.min.toFixed(4)} max=${stats.max.toFixed(4)} zc=${stats.zeroCrossings} rms=${stats.rms.toFixed(5)} ` +
        `nonzero=${stats.nonZeroFrames}/${stats.frames} first=${stats.firstAt} last=${stats.lastAt} ` +
        `ch=${String(ch ?? '?')} sr=${String(sr ?? '?')} ctxRate=${ctx.sampleRate} ctxCh=${ctx.destination.channelCount} ` +
        `constr=${JSON.stringify(constraints).slice(0, 120)} caps=${JSON.stringify(capabilities).slice(0, 120)} ctx=${ctx.state} → ${verdict}. Did you HEAR it?`,
      );
    } catch (err) {
      setRemoteResult(`measure failed: ${err instanceof Error ? err.message : 'unknown'}`);
    }
  }, [popupAudio, sharedContext, stopActiveBranch, sampleAnalyser, reportCtx, remoteGain]);

  const selectSink = useCallback(async (event: React.ChangeEvent<HTMLSelectElement>) => {
    setSinkResult('setting…');
    try {
      const el = popupAudio();
      if (!el) {
        setSinkResult('no audio element');
        return;
      }
      const withSink = el as HTMLAudioElement & { setSinkId?: (id: string) => Promise<void> };
      if (typeof withSink.setSinkId !== 'function') {
        setSinkResult('setSinkId unsupported');
        return;
      }
      await withSink.setSinkId(event.target.value);
      setSinkResult('sink set ok (dev-only, not persisted)');
      void refresh();
    } catch (err) {
      setSinkResult(`sink failed: ${err instanceof Error ? err.message : 'unknown'}`);
    }
  }, [popupAudio, refresh]);

  const inbound = media?.inboundAudio;
  const framesDecodedPresent = !!media?.inboundRaw && 'framesDecoded' in (media.inboundRaw as Record<string, unknown>);
  const deliveryLossPct = packetLossPercent(inbound?.packetsReceived ?? 0, inbound?.packetsLost ?? 0);
  const deliveryConceal = concealmentRatio(inbound?.totalSamplesReceived ?? 0, inbound?.concealedSamples ?? 0);
  const emittedCount = typeof media?.inboundRaw?.jitterBufferEmittedCount === 'number'
    ? (media.inboundRaw.jitterBufferEmittedCount as number)
    : 0;
  const deliveryConcealEmitted = concealmentShareEmitted(inbound?.concealedSamples ?? 0, emittedCount);
  return (
    <Card data-testid="audio-diagnostics" className="mt-3 border-dashed">
      <CardHeader className="px-4 py-2">
        <p className="text-xs font-semibold">Audio diagnostics (dev only, ?diag=1)</p>
        <p className="text-[11px] text-muted-foreground">Shared ctx: {ctxInfo}. Tone and remote-WebAudio use the SAME destination. The element path uses the element sink (default unless set above) — WebAudio always uses the default output.</p>
      </CardHeader>
      <CardContent className="space-y-2 px-4 pt-0 pb-3 text-xs">
        <p>Element: {element ? `muted=${fmt(element.muted)} vol=${fmt(element.volume)} paused=${fmt(element.paused)} ready=${fmt(element.readyState)} tracks=${element.audioTracks.length} sink=${fmt(element.sinkId || 'default')}` : '…'}</p>
        <p>Remote media: {media ? `pc=${fmt(media.connectionState)}/${fmt(media.iceConnectionState)} packetsReceived=${fmt(inbound?.packetsReceived)} bytesReceived=${fmt(inbound?.bytesReceived)} packetsLost=${fmt(inbound?.packetsLost)} jitter=${fmt(inbound?.jitter)} concealed(+silent)=${fmt(inbound?.concealedSamples)} totalSamples=${fmt(inbound?.totalSamplesReceived)} framesDecoded=${fmt(inbound?.framesDecoded)}${framesDecodedPresent ? '' : '(absent-in-browser)'} audioLevel=${fmt(media.inboundRaw?.audioLevel)} jbEmitted=${fmt(media.inboundRaw?.jitterBufferEmittedCount)} jbDelay=${fmt(inbound?.jitterBufferDelay)} codec=${fmt(inbound?.codec)} recvTrack=${fmt(media.receiverTrackId?.slice(0, 8))} live=${fmt(media.receiverLive)}` : 'no active call'}</p>
        <p data-testid="diag-delivery">Delivery (cumulative): {media && inbound ? `loss=${deliveryLossPct.toFixed(1)}% received=${fmt(inbound.totalSamplesReceived)} emitted=${emittedCount} concealed=${fmt(inbound.concealedSamples)} concealed/received=${(deliveryConceal * 100).toFixed(1)}% concealed/emitted=${(deliveryConcealEmitted * 100).toFixed(1)}%` : 'no active call'}</p>
        {media?.inboundRaw ? (
          <details>
            <summary className="cursor-pointer underline">inbound-rtp raw (allowlisted fields)</summary>
            <pre data-testid="diag-inbound-raw" className="overflow-x-auto whitespace-pre-wrap">{JSON.stringify(media.inboundRaw, null, 1)}</pre>
          </details>
        ) : null}
        <p>Outputs: supported={fmt(outputsSupported)} count={outputs.length} setSinkId={fmt(sinkSupported)}</p>
        {sinkSupported ? (
          <label className="flex items-center gap-2">
            Output device (dev-only, not persisted):
            <select data-testid="diag-sink-select" onChange={(e) => void selectSink(e)} defaultValue="">
              <option value="">Default</option>
              {outputs.map((o, i) => (
                <option key={`${i}-${o.labelPresent ? 'labeled' : 'unlabeled'}`} value={o.id}>{`output ${i + 1}${o.labelPresent ? ' (labeled)' : ''}`}</option>
              ))}
            </select>
          </label>
        ) : null}
        <p data-testid="diag-sink-result">{sinkResult}</p>
        <div className="flex flex-wrap gap-2">
          <Button type="button" size="sm" variant="outline" onClick={() => void playSharedTone()}>A: synthetic tone (shared ctx)</Button>
          <Button type="button" size="sm" variant="outline" onClick={() => void playElementTone()}>Tone via softphone element</Button>
          <Button type="button" size="sm" variant="outline" onClick={() => void enableAudio()}>Enable call audio</Button>
          <Button type="button" size="sm" variant="outline" onClick={() => void measureRemote({})}>B: remote via WebAudio (shared ctx)</Button>
          <Button type="button" size="sm" variant="outline" onClick={() => void measureRemote({ clone: true })}>B: cloned stream via shared ctx</Button>
          <Button type="button" size="sm" variant="outline" onClick={() => void playRemoteViaElement()}>C: remote via HTMLAudio element</Button>
        </div>
        <label className="flex items-center gap-2">
          Remote gain (dev-only):
          <select data-testid="diag-gain-select" value={remoteGain} onChange={(e) => setRemoteGain(e.target.value)}>
            <option value="0.5">0.5</option>
            <option value="1">1.0</option>
            <option value="2">2.0</option>
            <option value="4">4.0</option>
          </select>
        </label>
        <p data-testid="diag-tone-result">{toneResult}</p>
        <p data-testid="diag-element-tone-result">{elementToneResult}</p>
        <p data-testid="diag-gesture-result">{gestureResult}</p>
        <p data-testid="diag-htmlaudio-result">{htmlAudioResult}</p>
        <p data-testid="diag-energy-result">{remoteResult}</p>
        <p data-testid="diag-events">media events: {eventsLog.length === 0 ? '—' : eventsLog.join(' · ')}</p>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={heardTone} onChange={(e) => setHeardTone(e.target.checked)} /> I can hear the diagnostic tone
        </label>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={hearRemote} onChange={(e) => setHearRemote(e.target.checked)} /> I can hear the remote caller
        </label>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={hearEarbuds} onChange={(e) => setHearEarbuds(e.target.checked)} /> I can hear remote caller through earbuds
        </label>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={hearSpeakers} onChange={(e) => setHearSpeakers(e.target.checked)} /> I can hear remote caller through speakers
        </label>

        {/* ---- VOICE-07 FIX-15: hold/resume re-INVITE diagnostics (read-only) ---- */}
        <div data-testid="hold-diagnostics" className="mt-3 border-t pt-2">
          <p className="text-xs font-semibold">Hold / Resume re-INVITE diagnostics (FIX-15, observation only)</p>
          <p className="text-[11px] text-muted-foreground">
            Records the existing re-INVITE: transaction lifecycle, SIP status, redacted SDP direction, PeerConnection
            state, remote track identity, audio element and inbound RTP. It changes no signaling and no media.
          </p>
          <p data-testid="hold-diag-summary">
            traces={holdTraces.length} last={holdTraces.length > 0 ? `${holdTraces[holdTraces.length - 1]!.transactionId} ${holdTraces[holdTraces.length - 1]!.operation} → ${holdTraces[holdTraces.length - 1]!.classification?.primary ?? 'pending'}` : 'none — press HOLD then RESUME'}
          </p>
          {/* Machine-readable twin of the rendered traces (dev-only, already redacted). */}
          <pre data-testid="hold-diag-json" className="hidden">{JSON.stringify(holdTraces)}</pre>
          {holdTraces.map((t) => {
            const offer = t.outgoingSdp?.direction ?? UNAVAILABLE;
            const answer = t.answerSdp?.direction ?? UNAVAILABLE;
            const statuses = t.responses.length > 0 ? t.responses.map((r) => `${r.statusCode ?? '?'}${r.reasonPhrase ? ` ${r.reasonPhrase}` : ''}`).join(' → ') : UNAVAILABLE;
            return (
              <details key={t.transactionId} className="mt-1">
                <summary className="cursor-pointer underline">
                  {t.transactionId} ({t.operation}) held {fmt(t.holdStateBefore)} → {fmt(t.holdStateAfter)} · {fmt(t.durationMs)}ms ·{' '}
                  {t.classification?.primary ?? 'pending'}
                </summary>
                <pre data-testid={`hold-diag-${t.transactionId}`} className="overflow-x-auto whitespace-pre-wrap">
                  {[
                    `session states: ${t.events.filter((e) => e.stage === 'COMMAND_STARTED').map((e) => String((e.detail as { sessionState?: string }).sessionState)).join(',') || UNAVAILABLE}`,
                    `sip responses: ${statuses}`,
                    `ack observation: ${t.ackObservation}`,
                    `OUTGOING SDP direction=${offer} codecs=[${(t.outgoingSdp?.codecs ?? []).map((c) => `${c.payloadType}/${c.name}`).join(',')}] ice=${t.outgoingSdp ? fmt(t.outgoingSdp.iceUfragPresent && t.outgoingSdp.icePwdPresent) : UNAVAILABLE} candidates=${t.outgoingSdp?.candidateCount ?? UNAVAILABLE} rtcpMux=${t.outgoingSdp ? fmt(t.outgoingSdp.rtcpMux) : UNAVAILABLE}`,
                    `ANSWER   SDP direction=${answer} codecs=[${(t.answerSdp?.codecs ?? []).map((c) => `${c.payloadType}/${c.name}`).join(',')}] rtcpMux=${t.answerSdp ? fmt(t.answerSdp.rtcpMux) : UNAVAILABLE}`,
                    `modifiers still installed for re-INVITE: ${t.before?.pc.reinviteModifiersInstalled ?? UNAVAILABLE} holdModifierStillInstalled=${fmt(t.before?.pc.holdModifierStillInstalled)}`,
                    `pendingReinvite=${fmt(t.before?.pc.reinvitePending)}`,
                    `remote track: ${t.before ? `${t.before.remote.trackId ?? 'none'}:${t.before.remote.trackReadyState ?? UNAVAILABLE}:${fmt(t.before.remote.trackMuted)}` : UNAVAILABLE} → ${t.after ? `${t.after.remote.trackId ?? 'none'}:${t.after.remote.trackReadyState ?? UNAVAILABLE}:${fmt(t.after.remote.trackMuted)}` : UNAVAILABLE}`,
                    `transceivers: ${t.after ? t.after.pc.transceivers.map((x) => `${x.mid ?? '?'}:${x.direction ?? '?'}/${x.currentDirection ?? '?'} senderEn=${fmt(x.senderTrackEnabled)} recv=${x.receiverTrackId?.slice(0, 8) ?? 'none'}`).join(' | ') : UNAVAILABLE}`,
                    `audio element: ${t.after?.element ? `srcObject=${t.after.element.srcObjectId ?? 'none'} paused=${fmt(t.after.element.paused)} muted=${fmt(t.after.element.muted)} vol=${t.after.element.volume} ready=${t.after.element.readyState}` : UNAVAILABLE}`,
                    `rtp timeline: ${t.timeline.map((s) => `+${s.at - t.startedAt}ms pk=${s.rtp?.packetsReceived ?? UNAVAILABLE} by=${s.rtp?.bytesReceived ?? UNAVAILABLE} lost=${s.rtp?.packetsLost ?? UNAVAILABLE} lvl=${s.rtp?.audioLevel ?? UNAVAILABLE} ssrc=${s.rtp?.ssrc ?? UNAVAILABLE} pair=${s.rtp?.candidatePairState ?? UNAVAILABLE} rtt=${s.rtp?.rttSeconds ?? UNAVAILABLE}`).join('\n                ')}`,
                    t.error ? `error: ${t.error.name}/${t.error.kind} status=${t.error.statusCode ?? UNAVAILABLE} ${t.error.message}` : '',
                    '',
                    'CLASSIFICATION:',
                    ...(t.classification?.evidence ?? ['pending']).map((e) => `  - ${e}`),
                    '',
                    formatTransitionTable(buildTransitionTable(t)),
                  ].join('\n')}
                </pre>
              </details>
            );
          })}
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={heardMoh} onChange={(e) => setHeardMoh(e.target.checked)} /> I can hear Music-on-Hold while HELD
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={heardAfterResume} onChange={(e) => setHeardAfterResume(e.target.checked)} /> I can hear the caller again after RESUME
          </label>
        </div>
      </CardContent>
    </Card>
  );
}
