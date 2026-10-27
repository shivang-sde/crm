'use client';

/**
 * VOICE-06 — standalone softphone popup. OWNS the voice session
 * (VoiceRuntime + GenericSipAdapter + SIP.js + media). Isolated from tenant
 * layouts so CRM navigation/reload never recreates the runtime.
 *
 * Production pop-out UI sharing SoftphonePanel with the CRM widget; local
 * commands go straight to the host (same path remote commands use).
 */
import { Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { X } from 'lucide-react';
import { apiErrorMessage } from '@/lib/api/api-utils';
import { myCallingSettingsApi } from '@/lib/api/settings';
import { resolveGenericSipConnectorId } from '@/lib/voice';
import { SoftphoneHost } from '@/lib/voice/popup/host';
import type { SoftphoneSnapshot } from '@/lib/voice/popup/protocol';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { SoftphoneAudioDiagnostics } from '@/components/voice/SoftphoneAudioDiagnostics';
import { SoftphonePanel } from '@/components/voice/SoftphonePanel';

// Module singleton: exactly one runtime per popup, StrictMode-safe.
// Destroyed only on pagehide (host-owned), never on React remount.
let hostInstance: SoftphoneHost | null = null;

function getHost(): SoftphoneHost {
  if (!hostInstance) hostInstance = new SoftphoneHost();
  return hostInstance;
}

/** FIX-15: reuse the existing ?diag=1 voice diagnostic gate. */
function diagnosticsRequested(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    return new URLSearchParams(window.location.search).get('diag') === '1';
  } catch {
    return false;
  }
}

export default function SoftphonePage() {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [snapshot, setSnapshot] = useState<SoftphoneSnapshot | null>(null);
  const [genericConnectorId, setGenericConnectorId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const host = getHost();
    setSnapshot(host.mount({ audioElement: audioRef.current, diagnostics: diagnosticsRequested() }));
    const off = host.subscribe(setSnapshot);
    myCallingSettingsApi
      .getConnectors()
      .then((list) => {
        // Explicit Generic SIP resolution: never the first CALLING
        // connector (click2call stays with CLICK_TO_CALL).
        const id = resolveGenericSipConnectorId(list);
        setGenericConnectorId(id);
        host.setConnectorInstanceId(id);
      })
      .catch((err) => setError(apiErrorMessage(err, 'Unable to load calling connectors.')));
    return () => {
      off(); // keep the host (and SIP session) alive across remounts
    };
  }, []);

  const run = useCallback(async (label: string, fn: (host: SoftphoneHost) => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      const host = getHost();
      await fn(host);
      setSnapshot(host.snapshot());
    } catch (err) {
      setError(err instanceof Error ? `${label}: ${err.message}` : `${label} failed.`);
    } finally {
      setBusy(false);
    }
  }, []);

  const registration = snapshot?.voice.registration;
  const calls = snapshot?.voice.activeCalls ?? [];

  return (
    <main className="mx-auto w-full max-w-[420px] p-3">
      <Card className="shadow-lg">
        <CardHeader className="flex flex-row items-center justify-between gap-2 space-y-0 px-4 py-3">
          <h1 className="text-base font-semibold">Phone</h1>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            onClick={() => window.close()}
            aria-label="Close phone window"
            title="Close phone window"
          >
            <X className="size-4" aria-hidden="true" />
          </Button>
        </CardHeader>
        <CardContent className="px-4 pt-0 pb-4">
          <audio ref={audioRef} autoPlay />
          <SoftphonePanel
            registrationState={registration?.state ?? null}
            network={snapshot?.voice.network ?? 'UNKNOWN'}
            calls={calls}
            linkBusy={false}
            actionBusy={busy}
            error={error ?? registration?.lastError ?? null}
            retryCount={registration?.retryCount ?? null}
            nextRetryAt={registration?.nextRetryAt ?? null}
            onDial={(destination) => void run('Dial', (h) => h.dialLocal(destination))}
            onAnswer={(callId) => void run('Answer', (h) => h.answerLocal(callId))}
            onReject={(callId) => void run('Reject', (h) => h.rejectLocal(callId))}
            onHangup={(callId) => void run('Hangup', (h) => h.hangupLocal(callId))}
            onMute={(callId, muted) => void run(muted ? 'Mute' : 'Unmute', (h) => h.muteLocal(callId, muted))}
            onHold={(callId, held) => void run(held ? 'Hold' : 'Resume', (h) => h.holdLocal(callId, held))}
            onTransfer={(callId, destination) => void run('Transfer', (h) => h.transferLocal(callId, destination))}
            connectionSection={
              <div className="flex flex-col gap-2 rounded-lg border px-2.5 py-2">
                <p className="text-xs text-muted-foreground">
                  <span className="font-medium text-foreground">Generic SIP</span>
                  {' · Shared tenant connection'}
                </p>
                {genericConnectorId ? (
                  <div className="grid grid-cols-2 gap-1.5">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={busy}
                      onClick={() => void run('Connect', (h) => h.connectLocal(genericConnectorId))}
                    >
                      Connect
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={busy}
                      onClick={() => void run('Disconnect', (h) => h.disconnectLocal())}
                    >
                      Disconnect
                    </Button>
                  </div>
                ) : (
                  <p className="text-xs text-muted-foreground">
                    Generic SIP is not configured. Configure it under Settings → Voice.
                  </p>
                )}
              </div>
            }
          />
          <Suspense fallback={null}>
            <AudioDiagnosticsGate getHost={getHost} />
          </Suspense>
        </CardContent>
      </Card>
    </main>
  );
}

/** Development-only audio diagnostics, visible only with ?diag=1. */
function AudioDiagnosticsGate({ getHost }: { getHost: () => SoftphoneHost }) {
  let enabled = false;
  try {
    enabled = useSearchParams().get('diag') === '1';
  } catch {
    enabled = false;
  }
  if (!enabled) return null;
  return <SoftphoneAudioDiagnostics getHost={getHost} />;
}
