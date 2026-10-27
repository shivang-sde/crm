'use client';

/**
 * VOICE-04 — main-window test harness for the softphone link.
 * Diagnostic surface (like /softphone) that drives the REAL
 * SoftphoneController so Playwright can verify TEST 1–20 deterministically.
 * Not CRM UI; never touches SIP.js, media, or secrets.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { SoftphoneController } from '@/lib/voice/popup/controller';
import type { PopupCommandType } from '@/lib/voice/popup/protocol';
import type { SoftphoneSnapshot } from '@/lib/voice/popup/protocol';

interface HarnessApi {
  open: () => { ok: boolean; error?: string };
  handshake: (connectorInstanceId?: string) => Promise<{ ok: boolean; error?: string }>;
  send: (
    type: PopupCommandType,
    payload?: Record<string, unknown>,
    timeoutMs?: number,
  ) => Promise<{ ok: boolean; data?: unknown; error?: string; code?: string; causeCode?: string }>;
  status: () => {
    linkState: string;
    pendingCount: number;
    isPopupOpen: boolean;
    snapshot: SoftphoneSnapshot | null;
    events: number;
    snapshots: number;
  };
  hookRemount: () => void;
  secondAttach: () => Promise<{ ok: boolean; error?: string; instanceId?: string | null }>;
  reset: () => void;
}

declare global {
  interface Window {
    __softphoneTest?: HarnessApi;
  }
}

export default function SoftphoneTestPage() {
  const primary = useRef<SoftphoneController | null>(null);
  const secondary = useRef<SoftphoneController | null>(null);
  const eventCount = useRef(0);
  const snapshotCount = useRef(0);
  const [log, setLog] = useState<string[]>([]);
  const [hookKey, setHookKey] = useState(0);

  const appendLog = useCallback((line: string) => {
    setLog((prev) => [...prev.slice(-99), `${new Date().toISOString()} ${line}`]);
  }, []);

  const getPrimary = useCallback((): SoftphoneController => {
    if (!primary.current) {
      primary.current = new SoftphoneController();
      primary.current.onVoiceEvent(() => {
        eventCount.current += 1;
        appendLog('voice-event');
      });
      primary.current.onSnapshot(() => {
        snapshotCount.current += 1;
        appendLog('snapshot');
      });
    }
    return primary.current;
  }, [appendLog]);

  useEffect(() => {
    const api: HarnessApi = {
      open: () => {
        try {
          getPrimary().open();
          return { ok: true };
        } catch (err) {
          return { ok: false, error: err instanceof Error ? `${err.name}:${err.message}` : 'unknown' };
        }
      },
      handshake: async (connectorInstanceId?: string) => {
        try {
          await getPrimary().handshake(connectorInstanceId);
          return { ok: true };
        } catch (err) {
          return { ok: false, error: err instanceof Error ? `${err.name}:${(err as { code?: string }).code ?? ''}:${err.message}` : 'unknown' };
        }
      },
      send: async (type, payload = {}, timeoutMs?: number) => {
        try {
          const data = await getPrimary().sendCommand(
            type,
            payload as never,
            timeoutMs,
          );
          return { ok: true, data };
        } catch (err) {
          const code = (err as { code?: string }).code;
          const causeCode = (err as { causeCode?: string }).causeCode;
          return { ok: false, error: err instanceof Error ? err.message : 'unknown', code, causeCode };
        }
      },
      status: () => {
        const c = primary.current;
        return {
          linkState: c ? c.linkStateSnapshot : 'DISCONNECTED',
          pendingCount: (c as unknown as { pending?: Map<string, unknown> } | null)?.pending?.size ?? -1,
          isPopupOpen: c ? c.isPopupOpen : false,
          snapshot: c ? c.snapshot : null,
          events: eventCount.current,
          snapshots: snapshotCount.current,
        };
      },
      hookRemount: () => setHookKey((k) => k + 1),
      secondAttach: async () => {
        try {
          if (!secondary.current) secondary.current = new SoftphoneController();
          const popup = secondary.current.open();
          void popup;
          const snapshot = await secondary.current.handshake();
          return { ok: true, instanceId: snapshot.popupInstanceId };
        } catch (err) {
          return { ok: false, error: err instanceof Error ? `${err.name}:${err.message}` : 'unknown' };
        }
      },
      reset: () => {
        primary.current?.dispose();
        primary.current = null;
        secondary.current?.dispose();
        secondary.current = null;
        eventCount.current = 0;
        snapshotCount.current = 0;
        setLog([]);
      },
    };
    window.__softphoneTest = api;
    return () => {
      primary.current?.dispose();
      primary.current = null;
      secondary.current?.dispose();
      secondary.current = null;
      delete window.__softphoneTest;
    };
  }, [getPrimary]);

  return (
    <main style={{ padding: 16, fontFamily: 'system-ui, sans-serif', maxWidth: 640 }}>
      <h1 style={{ fontSize: 18, margin: '0 0 12px' }}>Softphone link test</h1>
      <HookProbe key={hookKey} />
      <pre data-testid="link-log" style={{ fontSize: 12, whiteSpace: 'pre-wrap', border: '1px solid #ddd', padding: 8, minHeight: 120 }}>
        {log.join('\n')}
      </pre>
      <MainWindowAudioProbe />
    </main>
  );
}

/**
 * FIX-9 Phase 5D — main-window local tone probe (dev harness only).
 * Proves the main window can render audible output independently of the
 * popup/SIP stack. Human acceptance: operator confirms audibility.
 */
function MainWindowAudioProbe() {
  const [result, setResult] = useState('idle');
  const [heard, setHeard] = useState(false);
  const run = useCallback(async () => {
    setResult('running…');
    try {
      const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!Ctor) {
        setResult('AudioContext unsupported');
        return;
      }
      const ctx = new Ctor();
      await ctx.resume().catch(() => undefined);
      const osc = ctx.createOscillator();
      osc.frequency.value = 660;
      const gain = ctx.createGain();
      gain.gain.value = 0.2;
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      await new Promise((resolve) => setTimeout(resolve, 1200));
      try { osc.stop(); } catch { /* noop */ }
      setResult(`tone done, ctx=${ctx.state}`);
      await ctx.close().catch(() => undefined);
    } catch (err) {
      setResult(`tone failed: ${err instanceof Error ? err.message : 'unknown'}`);
    }
  }, []);
  return (
    <div data-testid="main-audio-probe" style={{ marginTop: 12, border: '1px dashed #999', padding: 8 }}>
      <p style={{ fontSize: 12, fontWeight: 600 }}>Main-window audio probe (dev only)</p>
      <button type="button" onClick={() => void run()}>Play local tone (main window)</button>
      <p data-testid="main-audio-result" style={{ fontSize: 12 }}>{result}</p>
      <label style={{ fontSize: 12 }}>
        <input type="checkbox" checked={heard} onChange={(e) => setHeard(e.target.checked)} /> I can hear the main-window tone
      </label>
    </div>
  );
}

function HookProbe() {
  const ref = useRef<SoftphoneController | null>(null);
  const [state, setState] = useState('idle');
  useEffect(() => {
    let cancelled = false;
    const c = new SoftphoneController();
    ref.current = c;
    setState('mounting');
    c.handshake()
      .then(() => {
        if (!cancelled) setState(`ready:${c.linkStateSnapshot}`);
      })
      .catch(() => {
        if (!cancelled) setState(`error:${c.linkStateSnapshot}`);
      });
    return () => {
      cancelled = true;
      c.dispose();
      ref.current = null;
      setState('disposed');
    };
  }, []);
  return <div data-testid="hook-probe">{state}</div>;
}
