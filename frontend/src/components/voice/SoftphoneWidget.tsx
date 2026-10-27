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
import { ChevronDown, ExternalLink, Loader2, Phone, X } from 'lucide-react';
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

  // An inbound ring always forces the panel open so Answer/Reject are reachable.
  const isOpen = expanded || ringingInbound !== null;

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
    void controller
      .handshake(genericConnectorId ?? undefined)
      .catch(() => {
        /* popup may still be booting */
      });
  }, [genericConnectorId, getController]);

  const displayError = error ?? linkError;
  const busy = linkBusy || actionBusy;
  const isRegistered = registration?.state === 'REGISTERED';

  return (
    <div
      data-testid="softphone-widget"
      className="fixed right-[max(1rem,env(safe-area-inset-right))] bottom-[max(1rem,env(safe-area-inset-bottom))] z-40 flex max-w-[calc(100vw-2rem)] flex-col items-end"
      aria-label="Softphone"
    >
      {isOpen ? (
        <Card className="mb-3 max-h-[calc(100dvh-7rem)] w-[min(20rem,calc(100vw-2rem))] max-w-full overflow-hidden shadow-2xl duration-200 animate-in fade-in slide-in-from-bottom-2">
          <CardHeader className="flex flex-row items-center justify-between gap-2 space-y-0 px-3 py-2 bg-muted/30 border-b">
            <span className="inline-flex items-center gap-1.5 text-sm font-semibold">
              <Phone className="size-4 text-primary" aria-hidden="true" />
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
                onClick={() => setExpanded(false)}
                aria-label="Close phone"
                data-testid="softphone-toggle"
              >
                <X className="size-4" aria-hidden="true" />
              </Button>
            </span>
          </CardHeader>

          <CardContent className="max-h-[70vh] overflow-y-auto px-3 pt-3 pb-3">
            {connectorsFailed || !genericConnectorId ? (
              <div className="flex flex-col gap-2 rounded-lg border border-dashed p-3 text-center">
                <p className="text-sm font-medium">Generic SIP - Not configured</p>
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
        </Card>
      ) : null}

      {/* Floating Action Button */}
      <Button
        type="button"
        size="icon"
        onClick={() => setExpanded((prev) => !prev)}
        aria-label={isOpen ? 'Close softphone' : 'Open softphone'}
        aria-expanded={isOpen}
        data-testid="softphone-toggle"
        className={cn(
          'relative h-14 w-14 rounded-full shadow-lg transition-all duration-200 hover:scale-105',
          isRegistered ? 'bg-primary text-primary-foreground' : 'bg-secondary text-secondary-foreground border'
        )}
      >
        {busy ? (
          <Loader2 className="size-6 animate-spin" aria-hidden="true" />
        ) : (
          <Phone className="size-6" aria-hidden="true" />
        )}

        {/* Status/Call Count Indicator Badge */}
        {calls.length > 0 ? (
          <span className="absolute -top-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full bg-destructive text-[10px] font-bold text-destructive-foreground animate-pulse">
            {calls.length}
          </span>
        ) : isRegistered ? (
          <span className="absolute bottom-1 right-1 h-3.5 w-3.5 rounded-full border-2 border-background bg-green-500" />
        ) : null}
      </Button>
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
        {'- Shared tenant connection'}
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