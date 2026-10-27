'use client';

/**
 * VOICE-06 — shared softphone panel (dialer + call controls + status).
 * Fully presentational: driven by the popup-owned snapshot, commands flow
 * out through callbacks. Used by the CRM widget and the /softphone popup.
 */
import { useState } from 'react';
import {
  AlertCircle,
  ArrowRightLeft,
  Delete,
  Loader2,
  Mic,
  MicOff,
  Pause,
  Phone,
  PhoneIncoming,
  PhoneOff,
  Play,
} from 'lucide-react';
import type { VoiceCall, VoiceNetworkState, VoiceRegistrationState } from '@/lib/voice';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Dialpad } from './Dialpad';
import {
  callStateLabel,
  elapsedSeconds,
  formatElapsed,
  getRingingInbound,
  isDialableInput,
  isLiveCall,
  registrationDot,
  registrationLabel,
  sanitizeDialInput,
  useNow,
} from './softphone-utils';
import { cn } from '@/lib/utils';

export interface SoftphonePanelProps {
  registrationState: VoiceRegistrationState | null;
  network: VoiceNetworkState;
  calls: VoiceCall[];
  /** Handshake/connect in flight. */
  linkBusy: boolean;
  /** A call command in flight (disables control buttons). */
  actionBusy: boolean;
  error: string | null;
  /** VOICE-07 additive: reconnect attempt metadata for the countdown. */
  retryCount?: number | null;
  nextRetryAt?: string | null;
  onDial: (destination: string) => void;
  onAnswer: (callId: string) => void;
  onReject: (callId: string) => void;
  onHangup: (callId: string) => void;
  onMute: (callId: string, muted: boolean) => void;
  onHold: (callId: string, held: boolean) => void;
  onTransfer: (callId: string, destination: string) => void;
  connectionSection?: React.ReactNode;
  footer?: React.ReactNode;
}

const DOT_STYLES: Record<string, string> = {
  connected: 'bg-emerald-500',
  connecting: 'bg-amber-500 animate-pulse',
  disconnected: 'bg-slate-300',
  failed: 'bg-destructive',
};

export function SoftphonePanel(props: SoftphonePanelProps) {
  const {
    registrationState,
    network,
    calls,
    linkBusy,
    actionBusy,
    error,
    onDial,
    connectionSection,
    footer,
  } = props;
  const [dialValue, setDialValue] = useState('');
  const [transferOpen, setTransferOpen] = useState(false);
  const [transferTo, setTransferTo] = useState('');
  const liveCalls = calls.filter(isLiveCall);
  const ringingInbound = getRingingInbound(calls);
  const now = useNow(liveCalls.length > 0 || registrationState === 'RECONNECTING');
  const busy = linkBusy || actionBusy;
  // VOICE-07 §12: dial is only meaningful against a live registration.
  // The adapter rejects otherwise; the UI disables first with a clear hint.
  const registered = registrationState === 'REGISTERED';
  const reconnecting =
    registrationState === 'CONNECTING' ||
    registrationState === 'REGISTERING' ||
    registrationState === 'RECONNECTING';

  const dialDisabled =
    busy ||
    !registered ||
    !isDialableInput(dialValue.trim()) ||
    liveCalls.some((call) => call.direction === 'OUTBOUND' && (call.state === 'DIALING' || call.state === 'CONNECTED'));

  const retryInSeconds =
    registrationState === 'RECONNECTING' && props.nextRetryAt
      ? Math.max(0, Math.ceil((Date.parse(props.nextRetryAt) - now) / 1000))
      : null;

  const handleDialKey = (digit: string) => {
    setDialValue((prev) => sanitizeDialInput(prev + digit));
  };

  const handleDial = () => {
    const destination = dialValue.trim();
    if (!isDialableInput(destination) || dialDisabled) return;
    onDial(destination);
  };

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-2" aria-live="polite">
        <span className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
          <span
            aria-hidden="true"
            className={cn('inline-block size-2 rounded-full', DOT_STYLES[registrationDot(registrationState)] ?? DOT_STYLES.disconnected)}
          />
          {registrationLabel(registrationState, network, linkBusy)}
        </span>
        {network !== 'UNKNOWN' && network !== 'ONLINE' ? (
          <Badge variant="outline" className="text-[11px]">
            Net: {network}
          </Badge>
        ) : null}
      </div>

      {registrationState === 'RECONNECTING' ? (
        <p className="text-xs text-muted-foreground" aria-live="polite">
          Reconnecting{typeof props.retryCount === 'number' && props.retryCount > 0 ? ` · attempt ${props.retryCount}` : ''}
          {retryInSeconds !== null && !Number.isNaN(retryInSeconds) ? ` in ${retryInSeconds}s` : ''}…
        </p>
      ) : null}

      {connectionSection}

      {ringingInbound ? (
        <div
          role="alertdialog"
          aria-label={`Incoming call from ${ringingInbound.destination}`}
          className="rounded-lg border border-primary/30 bg-primary/5 p-3"
        >
          <p className="flex items-center gap-1.5 text-sm font-semibold">
            <PhoneIncoming className="size-4" aria-hidden="true" />
            Incoming call
          </p>
          <p className="mt-0.5 truncate text-sm text-muted-foreground">{ringingInbound.destination}</p>
          <div className="mt-2 grid grid-cols-1 gap-2 md:grid-cols-2">
            <Button
              type="button"
              disabled={busy}
              onClick={() => props.onReject(ringingInbound.callId)}
              variant="outline"
              aria-label={`Reject call from ${ringingInbound.destination}`}
            >
              <PhoneOff className="size-4" aria-hidden="true" /> Reject
            </Button>
            <Button
              type="button"
              disabled={busy}
              onClick={() => props.onAnswer(ringingInbound.callId)}
              aria-label={`Answer call from ${ringingInbound.destination}`}
            >
              <Phone className="size-4" aria-hidden="true" /> Answer
            </Button>
          </div>
        </div>
      ) : null}

      {liveCalls.map((call) => (
        <ActiveCallCard
          key={call.callId}
          call={call}
          now={now}
          busy={busy}
          transferOpen={transferOpen}
          transferTo={transferTo}
          onTransferOpenChange={setTransferOpen}
          onTransferToChange={setTransferTo}
          onHangup={() => props.onHangup(call.callId)}
          onMute={() => props.onMute(call.callId, !call.muted)}
          onHold={() => props.onHold(call.callId, !call.held)}
          onTransfer={(destination) => props.onTransfer(call.callId, destination)}
        />
      ))}

      <div className="flex flex-col gap-2">
        <Label htmlFor="softphone-number">Number</Label>
        <div className="flex gap-1.5">
          <Input
            id="softphone-number"
            value={dialValue}
            onChange={(event) => setDialValue(sanitizeDialInput(event.target.value))}
            onKeyDown={(event) => {
              if (event.key === 'Enter') handleDial();
            }}
            placeholder="Extension or number"
            inputMode="tel"
            autoComplete="off"
            disabled={busy}
            className="h-10 text-lg tracking-wide"
          />
          <Button
            type="button"
            variant="ghost"
            size="icon"
            disabled={busy || dialValue.length === 0}
            onClick={() => setDialValue((prev) => prev.slice(0, -1))}
            aria-label="Backspace"
          >
            <Delete className="size-4" aria-hidden="true" />
          </Button>
        </div>
        <Dialpad disabled={busy} onKey={handleDialKey} />
        <Button type="button" disabled={dialDisabled} onClick={handleDial} aria-label="Dial number">
          {busy ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Phone className="size-4" aria-hidden="true" />}
          Dial
        </Button>
        {reconnecting && liveCalls.length === 0 && !ringingInbound ? (
          <p className="text-xs text-muted-foreground">Connecting to phone…</p>
        ) : null}
      </div>

      {error ? (
        <p role="alert" className="flex items-start gap-1.5 text-xs text-destructive">
          <AlertCircle className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
          <span>{error}</span>
        </p>
      ) : null}

      {footer}
    </div>
  );
}

function ActiveCallCard(props: {
  call: VoiceCall;
  now: number;
  busy: boolean;
  transferOpen: boolean;
  transferTo: string;
  onTransferOpenChange: (open: boolean) => void;
  onTransferToChange: (value: string) => void;
  onHangup: () => void;
  onMute: () => void;
  onHold: () => void;
  onTransfer: (destination: string) => void;
}) {
  const { call, now, busy } = props;
  const elapsed = formatElapsed(elapsedSeconds(call.startedAt, call.endedAt, now));
  const ended = !isLiveCall(call);

  return (
    <div className="rounded-lg border bg-card p-3" aria-live="polite">
      <div className="flex items-center justify-between gap-2">
        <p className="min-w-0">
          <span className="block truncate text-sm font-semibold">{call.destination}</span>
          <span className="text-xs text-muted-foreground">
            {callStateLabel(call)} · <time>{elapsed}</time>
            {call.direction === 'INBOUND' ? ' · Inbound' : null}
          </span>
        </p>
        <Badge variant={call.held ? 'secondary' : 'default'} className="shrink-0 text-[11px]">
          {call.held ? 'Held' : callStateLabel(call)}
        </Badge>
      </div>

      {!ended ? (
        <div className="mt-2 grid grid-cols-4 gap-1.5">
          <Button
            type="button"
            variant={call.muted ? 'secondary' : 'outline'}
            size="sm"
            disabled={busy}
            onClick={props.onMute}
            aria-label={call.muted ? 'Unmute microphone' : 'Mute microphone'}
            aria-pressed={call.muted}
          >
            {call.muted ? <MicOff className="size-3.5" aria-hidden="true" /> : <Mic className="size-3.5" aria-hidden="true" />}
            {call.muted ? 'Muted' : 'Mute'}
          </Button>
          <Button
            type="button"
            variant={call.held ? 'secondary' : 'outline'}
            size="sm"
            disabled={busy}
            onClick={props.onHold}
            aria-label={call.held ? 'Resume call' : 'Hold call'}
            aria-pressed={call.held}
          >
            {call.held ? <Play className="size-3.5" aria-hidden="true" /> : <Pause className="size-3.5" aria-hidden="true" />}
            {call.held ? 'Resume' : 'Hold'}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={busy}
            onClick={() => props.onTransferOpenChange(!props.transferOpen)}
            aria-label="Transfer call"
            aria-expanded={props.transferOpen}
          >
            <ArrowRightLeft className="size-3.5" aria-hidden="true" />
            Transfer
          </Button>
          <Button
            type="button"
            variant="destructive"
            size="sm"
            disabled={busy}
            onClick={props.onHangup}
            aria-label={`Hang up call with ${call.destination}`}
          >
            <PhoneOff className="size-3.5" aria-hidden="true" />
            End
          </Button>
        </div>
      ) : null}

      {!ended && props.transferOpen ? (
        <div className="mt-2 flex gap-1.5">
          <Label htmlFor={`transfer-to-${call.callId}`} className="sr-only">
            Transfer destination
          </Label>
          <Input
            id={`transfer-to-${call.callId}`}
            value={props.transferTo}
            onChange={(event) => props.onTransferToChange(sanitizeDialInput(event.target.value))}
            placeholder="Transfer to…"
            inputMode="tel"
            autoComplete="off"
            disabled={busy}
            className="h-8 text-sm"
          />
          <Button
            type="button"
            size="sm"
            disabled={busy || !isDialableInput(props.transferTo.trim())}
            onClick={() => {
              props.onTransfer(props.transferTo.trim());
              props.onTransferToChange('');
              props.onTransferOpenChange(false);
            }}
          >
            Send
          </Button>
        </div>
      ) : null}
    </div>
  );
}
