'use client';

/**
 * VOICE-03 — minimal reusable main-window binding for the softphone popup.
 * Owns one SoftphoneController per mount; never touches SIP.js or secrets.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  SoftphoneController,
  type SoftphoneControllerOptions,
} from './controller';
import type { VoiceForwardPayload } from './protocol';
import type { SoftphoneSnapshot } from './protocol';

export function useSoftphoneController(options?: SoftphoneControllerOptions) {
  const ref = useRef<SoftphoneController | null>(null);
  // Lazily (re)created: StrictMode unmount disposes the controller, and the
  // next access after remount transparently owns a fresh one. Never touches
  // the popup itself — only this window's binding.
  const getController = useCallback((): SoftphoneController => {
    if (!ref.current) ref.current = new SoftphoneController(options);
    return ref.current;
  }, []);
  const [snapshot, setSnapshot] = useState<SoftphoneSnapshot | null>(null);
  const [lastEvent, setLastEvent] = useState<VoiceForwardPayload | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const controller = getController();
    const offSnapshot = controller.onSnapshot(setSnapshot);
    const offEvent = controller.onVoiceEvent(setLastEvent);
    return () => {
      offSnapshot();
      offEvent();
    };
  }, [getController]);

  const open = useCallback(() => {
    try {
      getController().open();
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to open the softphone.');
    }
  }, [getController]);

  const connect = useCallback(async (connectorInstanceId?: string) => {
    setBusy(true);
    setError(null);
    try {
      const controller = getController();
      await controller.handshake(connectorInstanceId);
      await controller.sendCommand('VOICE_CONNECT', { connectorInstanceId });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Softphone command failed.');
    } finally {
      setBusy(false);
    }
  }, [getController]);

  useEffect(() => {
    const controller = ref.current;
    return () => {
      // Dispose on unmount: listeners/timers cleared. The POPUP stays alive —
      // only this window's binding is torn down (re-handshake on remount).
      controller?.dispose();
      ref.current = null;
    };
  }, []);

  return { snapshot, lastEvent, error, busy, open, connect, getController };
}
