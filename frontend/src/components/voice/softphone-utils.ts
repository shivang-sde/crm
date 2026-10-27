'use client';

/**
 * VOICE-06 — pure softphone presentation helpers (no SIP, no React state
 * except the clock hook). Shared by the CRM widget and the popup panel.
 */
import { useEffect, useState } from 'react';
import type { VoiceCall, VoiceCallState, VoiceNetworkState, VoiceRegistrationState } from '@/lib/voice';

export const TERMINAL_CALL_STATES: ReadonlySet<VoiceCallState> = new Set(['ENDED', 'FAILED']);

export function isLiveCall(call: VoiceCall): boolean {
  return !TERMINAL_CALL_STATES.has(call.state);
}

/** First call worth showing controls for (prefers non-terminal). */
export function getPrimaryCall(calls: VoiceCall[]): VoiceCall | null {
  return calls.find((call) => isLiveCall(call)) ?? calls[0] ?? null;
}

export function getRingingInbound(calls: VoiceCall[]): VoiceCall | null {
  return calls.find((call) => call.direction === 'INBOUND' && call.state === 'RINGING') ?? null;
}

/** Seconds between an ISO timestamp and now (end defaults to now). */
export function elapsedSeconds(startedAt: string, endedAt: string | null, now: number): number {
  const start = Date.parse(startedAt);
  if (Number.isNaN(start)) return 0;
  const end = endedAt ? Date.parse(endedAt) : now;
  const resolved = Number.isNaN(end) ? now : end;
  return Math.max(0, Math.round((resolved - start) / 1000));
}

export function formatElapsed(totalSeconds: number): string {
  const clamped = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(clamped / 3600);
  const minutes = Math.floor((clamped % 3600) / 60);
  const seconds = clamped % 60;
  const mm = hours > 0 ? String(minutes).padStart(2, '0') : String(minutes);
  const ss = String(seconds).padStart(2, '0');
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
}

/** Ticks once per second while active; cleans up on unmount/deactivation. */
export function useNow(active: boolean): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [active]);
  return now;
}

const DIALABLE_PATTERN = /^[0-9*#+,;]+$/;

export function isDialableInput(value: string): boolean {
  return value.length > 0 && DIALABLE_PATTERN.test(value);
}

/** Keep only characters the dial contract accepts; never normalize the rest. */
export function sanitizeDialInput(value: string): string {
  return value.replace(/[^0-9*#+,;]/g, '');
}

export type ConnectionDot = 'connected' | 'connecting' | 'disconnected' | 'failed';

export function registrationDot(state: VoiceRegistrationState | null | undefined): ConnectionDot {
  switch (state) {
    case 'REGISTERED':
      return 'connected';
    case 'CONNECTING':
    case 'REGISTERING':
    case 'RECONNECTING':
      return 'connecting';
    case 'FAILED':
      return 'failed';
    default:
      return 'disconnected';
  }
}

export function registrationLabel(
  state: VoiceRegistrationState | null | undefined,
  network: VoiceNetworkState,
  linkBusy: boolean,
): string {
  if (linkBusy) return 'Working…';
  if (network === 'OFFLINE') return 'Offline';
  switch (state) {
    case 'REGISTERED':
      return 'Connected';
    case 'CONNECTING':
      return 'Connecting…';
    case 'REGISTERING':
      return 'Registering…';
    case 'RECONNECTING':
      return 'Reconnecting…';
    case 'FAILED':
      return 'Connection failed';
    default:
      return 'Disconnected';
  }
}

export function callStateLabel(call: VoiceCall): string {
  switch (call.state) {
    case 'DIALING':
      return call.direction === 'INBOUND' ? 'Incoming…' : 'Calling…';
    case 'RINGING':
      return call.direction === 'INBOUND' ? 'Incoming call' : 'Ringing…';
    case 'CONNECTED':
      return call.held ? 'On hold' : 'Active';
    case 'HELD':
      return 'On hold';
    case 'ENDING':
      return 'Ending…';
    case 'ENDED':
      return 'Ended';
    case 'FAILED':
      return 'Failed';
    default:
      return 'Idle';
  }
}
