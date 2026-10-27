"use client";

import { useEffect, useRef, useState } from "react";
import { Download, WifiOff, X } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";

const UPDATE_TOAST_ID = "pwa-update-available";
const INSTALL_DISMISS_KEY = "sellspark:pwa-install-dismissed";
const INSTALL_DISMISS_MS = 30 * 24 * 60 * 60 * 1000;
const SW_ENABLED = process.env.NODE_ENV === "production";

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed"; platform: string }>;
}

function isStandalone() {
  return (
    window.matchMedia("(display-mode: standalone)").matches ||
    (window.navigator as Navigator & { standalone?: boolean }).standalone === true
  );
}

function isIosDevice() {
  return /iphone|ipad|ipod/i.test(window.navigator.userAgent);
}

function installPromptDismissed() {
  try {
    const dismissedAt = Number(window.localStorage.getItem(INSTALL_DISMISS_KEY));
    return Number.isFinite(dismissedAt) && Date.now() - dismissedAt < INSTALL_DISMISS_MS;
  } catch {
    return false;
  }
}

export function PwaManager() {
  const [online, setOnline] = useState(true);
  const [installEvent, setInstallEvent] = useState<BeforeInstallPromptEvent | null>(null);
  const [showIosInstall, setShowIosInstall] = useState(false);
  const updateAccepted = useRef(false);

  useEffect(() => {
    const updateOnlineState = () => setOnline(window.navigator.onLine);

    updateOnlineState();
    window.addEventListener("online", updateOnlineState);
    window.addEventListener("offline", updateOnlineState);

    return () => {
      window.removeEventListener("online", updateOnlineState);
      window.removeEventListener("offline", updateOnlineState);
    };
  }, []);

  useEffect(() => {
    if (!SW_ENABLED || !("serviceWorker" in navigator)) return;

    let cancelled = false;
    let registration: ServiceWorkerRegistration | undefined;
    let updateInterval: number | undefined;

    const showUpdate = (worker: ServiceWorker) => {
      toast("Update available", {
        id: UPDATE_TOAST_ID,
        description: "Reload to use the latest version. Finish any open form first.",
        duration: Infinity,
        action: {
          label: "Reload",
          onClick: () => {
            updateAccepted.current = true;
            worker.postMessage({ type: "SKIP_WAITING" });
          },
        },
      });
    };

    const handleControllerChange = () => {
      if (updateAccepted.current) {
        window.location.reload();
      }
    };

    const register = async () => {
      try {
        registration = await navigator.serviceWorker.register("/sw.js", { scope: "/" });
        if (cancelled) return;

        if (registration.waiting && navigator.serviceWorker.controller) {
          showUpdate(registration.waiting);
        }

        registration.addEventListener("updatefound", () => {
          const worker = registration?.installing;
          if (!worker) return;

          worker.addEventListener("statechange", () => {
            if (worker.state === "installed" && navigator.serviceWorker.controller) {
              showUpdate(worker);
            }
          });
        });

        updateInterval = window.setInterval(() => {
          void registration?.update();
        }, 60 * 60 * 1000);
      } catch (error) {
        console.warn("Service worker registration failed", error);
      }
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        void registration?.update();
      }
    };

    navigator.serviceWorker.addEventListener("controllerchange", handleControllerChange);
    document.addEventListener("visibilitychange", handleVisibilityChange);

    if (document.readyState === "complete") {
      void register();
    } else {
      window.addEventListener("load", register, { once: true });
    }

    return () => {
      cancelled = true;
      window.removeEventListener("load", register);
      navigator.serviceWorker.removeEventListener("controllerchange", handleControllerChange);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      if (updateInterval) window.clearInterval(updateInterval);
    };
  }, []);

  useEffect(() => {
    if (!SW_ENABLED || isStandalone()) return;

    const handleBeforeInstall = (event: Event) => {
      event.preventDefault();
      if (!installPromptDismissed()) {
        setInstallEvent(event as BeforeInstallPromptEvent);
      }
    };

    const handleInstalled = () => {
      setInstallEvent(null);
      setShowIosInstall(false);
    };

    window.addEventListener("beforeinstallprompt", handleBeforeInstall);
    window.addEventListener("appinstalled", handleInstalled);

    let iosHintTimer: number | undefined;
    if (isIosDevice() && !installPromptDismissed()) {
      iosHintTimer = window.setTimeout(() => setShowIosInstall(true), 5000);
    }

    return () => {
      window.removeEventListener("beforeinstallprompt", handleBeforeInstall);
      window.removeEventListener("appinstalled", handleInstalled);
      if (iosHintTimer) window.clearTimeout(iosHintTimer);
    };
  }, []);

  const dismissInstall = () => {
    setInstallEvent(null);
    setShowIosInstall(false);
    try {
      window.localStorage.setItem(INSTALL_DISMISS_KEY, String(Date.now()));
    } catch {
      // Storage can be unavailable in private browsing; dismissal still applies for this session.
    }
  };

  const installApp = async () => {
    if (!installEvent) return;

    await installEvent.prompt();
    const choice = await installEvent.userChoice;

    if (choice.outcome === "accepted") {
      setInstallEvent(null);
      setShowIosInstall(false);
    } else {
      dismissInstall();
    }
  };

  const showInstallPrompt = online && (installEvent !== null || showIosInstall);

  return (
    <>
      {!online ? (
        <div
          role="status"
          aria-live="polite"
          className="fixed inset-x-0 top-[env(safe-area-inset-top)] z-[70] flex items-center justify-center gap-2 bg-amber-600 px-4 py-2 text-center text-xs font-medium text-white"
        >
          <WifiOff className="h-4 w-4 shrink-0" aria-hidden="true" />
          <span>You are offline. Changes cannot be saved until the connection returns.</span>
        </div>
      ) : null}

      {showInstallPrompt ? (
        <div
          role="dialog"
          aria-label="Install Sellspark CRM"
          className="fixed bottom-[calc(5.5rem+env(safe-area-inset-bottom))] left-[max(1rem,env(safe-area-inset-left))] right-[max(1rem,env(safe-area-inset-right))] z-30 mx-auto max-w-sm rounded-xl border bg-white p-3 shadow-lg sm:left-4 sm:right-auto sm:w-80"
        >
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-indigo-600 text-white">
              <Download className="h-5 w-5" aria-hidden="true" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-slate-900">Install Sellspark CRM</p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                {showIosInstall
                  ? "Tap Share, then Add to Home Screen to install this app."
                  : "Open your CRM faster from your home screen."}
              </p>
              {!showIosInstall ? (
                <Button type="button" size="sm" className="mt-3" onClick={() => void installApp()}>
                  Install
                </Button>
              ) : null}
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              onClick={dismissInstall}
              aria-label="Dismiss install prompt"
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
        </div>
      ) : null}
    </>
  );
}
