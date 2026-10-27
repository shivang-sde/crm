'use client';

/**
 * VOICE-06 — persistent CRM softphone widget (bottom-right).
 *
 * Remote UI only: all voice operations go through SoftphoneController to
 * the popup-owned runtime (VOICE-03/04). Never touches SIP.js, adapter,
 * media, or secrets. Mounted once from the tenant shell (AppLayout), so
 * normal SPA navigation never unmounts it.
 */
import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { ChevronDown, ChevronUp, ExternalLink, Loader2, Phone } from 'lucide-react';
import { toast } from 'sonner';
import type { SoftphoneController } from '@/lib/voice/popup/controller';
import type { PopupCommandPayloads, PopupCommandType } from '@/lib/voice/popup/protocol';
import { myCallingSettingsApi } from '@/lib/api/settings';
import { resolveGenericSipConnectorId } from '@/lib/voice';
import { useSoftphoneController } from '@/lib/voice/popup/use-softphone';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { SoftphonePanel } from './SoftphonePanel';
import { getRingingInbound } from './softphone-utils';
import { cn } from '@/lib/utils';

function commandErrorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  return 'Softphone command failed.';
}

export function SoftphoneWidget() {
  const { snapshot, error: linkError, busy: linkBusy, getController } = useSoftphoneController();
  const [hasConnectors, setHasConnectors] = useState(false);
  const [genericConnectorId, setGenericConnectorId] = useState<string | null>(null);
  const [connectorsFailed, setConnectorsFailed] = useState(false);
  const [actionBusy, setActionBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(false);

  const registration = snapshot?.voice.registration ?? null;
  const network = snapshot?.voice.network ?? 'UNKNOWN';
  const calls = snapshot?.voice.activeCalls ?? [];
  const ringingInbound = getRingingInbound(calls);

  useEffect(() => {
    let cancelled = false;
    myCallingSettingsApi
      .getConnectors()
      .then((list) => {
        if (cancelled) return;
        // Explicit Generic SIP resolution: never the first CALLING
        // connector (e.g. click2call stays with CLICK_TO_CALL).
        setHasConnectors(list.length > 0);
        setGenericConnectorId(resolveGenericSipConnectorId(list));
      })
      .catch(() => {
        if (!cancelled) setConnectorsFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Derived, not state: an inbound ring always forces the panel open so
  // Answer/Reject are reachable. (No setState-in-effect needed.)
  const isOpen = expanded || ringingInbound !== null;

  // Bounded attach retries: a cold popup may still be booting (JS load, host
  // mount) when the first handshake deadline expires. Mirror the resilience
  // harness strategy — every attempt reuses existing controller mechanisms
  // (open + handshake); the now-warm popup answers the next round.
  const ensureLinked = useCallback(
    async (controller: SoftphoneController): Promise<void> => {
      if (controller.linkStateSnapshot === 'READY') return;
      controller.open();
      let lastError: unknown = null;
      for (let attempt = 1; attempt <= 3; attempt += 1) {
        try {
          await controller.handshake(genericConnectorId ?? undefined);
          return;
        } catch (err) {
          lastError = err;
          if (!controller.isPopupOpen) break;
          await new Promise((resolve) => setTimeout(resolve, 1000));
        }
      }
      throw lastError instanceof Error ? lastError : new Error('Softphone popup did not respond.');
    },
    [genericConnectorId],
  );

  const runCommand = useCallback(
    async <T extends PopupCommandType>(label: string, type: T, payload: PopupCommandPayloads[T]): Promise<void> => {
      setActionBusy(true);
      setError(null);
      try {
        const controller = getController();
        await ensureLinked(controller);
        await controller.sendCommand(type, payload);
      } catch (err) {
        const message = commandErrorMessage(err);
        setError(`${label}: ${message}`);
        toast.error(`${label} failed`, { description: message });
      } finally {
        setActionBusy(false);
      }
    },
    [ensureLinked, getController],
  );

  const handleConnect = useCallback(() => {
    void runCommand('Connect', 'VOICE_CONNECT', { connectorInstanceId: genericConnectorId ?? undefined });
  }, [genericConnectorId, runCommand]);

  const handlePopOut = useCallback(() => {
    const controller = getController();
    try {
      controller.open();
      setError(null);
    } catch (err) {
      const message = commandErrorMessage(err);
      setError(message);
      toast.error('Could not open phone window', { description: message });
      return;
    }
    // Best-effort sync: pull the authoritative snapshot so the widget
    // reflects popup-owned state even if the user never connected here.
    void controller
      .handshake(genericConnectorId ?? undefined)
      .catch(() => {
        /* popup may still be booting; later actions re-handshake */
      });
  }, [genericConnectorId, getController]);

  const displayError = error ?? linkError;
  const busy = linkBusy || actionBusy;

  return (
    <div
      data-testid="softphone-widget"
      className="fixed right-4 bottom-4 z-40 w-[320px] max-w-[calc(100vw-2rem)]"
      aria-label="Softphone"
    >
      <Card className="overflow-hidden shadow-xl">
        <CardHeader className="flex flex-row items-center justify-between gap-2 space-y-0 px-3 py-2">
          <span className="inline-flex items-center gap-1.5 text-sm font-semibold">
            <Phone className="size-4" aria-hidden="true" />
            Softphone
          </span>
          <span className="inline-flex items-center gap-1">
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              onClick={handlePopOut}
              aria-label="Pop out phone window"
              title="Pop out phone window"
            >
              <ExternalLink className="size-4" aria-hidden="true" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              onClick={() => setExpanded((prev) => !prev)}
              aria-label={isOpen ? 'Collapse phone' : 'Expand phone'}
              aria-expanded={isOpen}
              data-testid="softphone-toggle"
            >
              {isOpen ? <ChevronDown className="size-4" aria-hidden="true" /> : <ChevronUp className="size-4" aria-hidden="true" />}
            </Button>
          </span>
        </CardHeader>

        {isOpen ? (
          <CardContent className="max-h-[70vh] overflow-y-auto px-3 pt-0 pb-3">
            {connectorsFailed || !genericConnectorId ? (
              <div className="flex flex-col gap-2 rounded-lg border border-dashed p-3 text-center">
                <p className="text-sm font-medium">Generic SIP · Not configured</p>
                <p className="text-xs text-muted-foreground">
                  {connectorsFailed
                    ? 'Could not load calling connections.'
                    : hasConnectors
                      ? 'No Generic SIP connection is set up yet.'
                      : 'No calling connection is set up yet.'}
                </p>
                <Button type="button" variant="outline" size="sm" asChild>
                  <Link href="/settings/voice">Open Voice Settings</Link>
                </Button>
              </div>
            ) : (
              <SoftphonePanel
                registrationState={registration?.state ?? null}
                network={network}
                calls={calls}
                linkBusy={linkBusy}
                actionBusy={actionBusy}
                error={displayError ?? registration?.lastError ?? null}
                retryCount={registration?.retryCount ?? null}
                nextRetryAt={registration?.nextRetryAt ?? null}
                onDial={(destination) => void runCommand('Dial', 'VOICE_DIAL', { destination })}
                onAnswer={(callId) => void runCommand('Answer', 'VOICE_ANSWER', { callId })}
                onReject={(callId) => void runCommand('Reject', 'VOICE_REJECT', { callId })}
                onHangup={(callId) => void runCommand('Hang up', 'VOICE_HANGUP', { callId })}
                onMute={(callId, muted) =>
                  void runCommand(muted ? 'Mute' : 'Unmute', muted ? 'VOICE_MUTE' : 'VOICE_UNMUTE', { callId })
                }
                onHold={(callId, held) =>
                  void runCommand(held ? 'Hold' : 'Resume', held ? 'VOICE_HOLD' : 'VOICE_RESUME', { callId })
                }
                onTransfer={(callId, destination) =>
                  void runCommand('Transfer', 'VOICE_TRANSFER', { callId, destination })
                }
                connectionSection={
                  <ConnectionSection
                    registered={registration?.registered ?? false}
                    busy={busy}
                    onConnect={handleConnect}
                    onDisconnect={() => void runCommand('Disconnect', 'VOICE_DISCONNECT', {})}
                  />
                }
                footer={
                  <p className="text-[11px] text-muted-foreground">
                    Audio runs in the phone window.{' '}
                    <Link href="/settings/voice" className="underline underline-offset-2">
                      Connection settings
                    </Link>
                  </p>
                }
              />
            )}
          </CardContent>
        ) : (
          <button
            type="button"
            onClick={() => setExpanded(true)}
            aria-label={`Expand phone, ${registration?.state === 'REGISTERED' ? 'connected' : 'not connected'}`}
            className={cn(
              'flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-xs text-muted-foreground',
              'hover:bg-muted/50 focus-visible:outline-2 focus-visible:outline-ring',
            )}
          >
            <span>
              {registration?.state === 'REGISTERED'
                ? `Connected${calls.length > 0 ? ` · ${calls.length} call${calls.length === 1 ? '' : 's'}` : ''}`
                : registration?.state === 'RECONNECTING'
                  ? 'Reconnecting…'
                  : 'Phone · click to open'}
            </span>
            {busy ? <Loader2 className="size-3.5 animate-spin" aria-hidden="true" /> : null}
          </button>
        )}
      </Card>
    </div>
  );
}

function ConnectionSection(props: {
  registered: boolean;
  busy: boolean;
  onConnect: () => void;
  onDisconnect: () => void;
}) {
  return (
    <div className="flex items-center justify-between gap-2 rounded-lg border px-2.5 py-2">
      <p className="min-w-0 truncate text-xs text-muted-foreground">
        <span className="font-medium text-foreground">Generic SIP</span>
        {' · Shared tenant connection'}
      </p>
      {props.registered ? (
        <Button type="button" variant="outline" size="xs" disabled={props.busy} onClick={props.onDisconnect}>
          Disconnect
        </Button>
      ) : (
        <Button type="button" variant="default" size="xs" disabled={props.busy} onClick={props.onConnect}>
          {props.busy ? <Loader2 className="size-3 animate-spin" aria-hidden="true" /> : null}
          Connect
        </Button>
      )}
    </div>
  );
}
