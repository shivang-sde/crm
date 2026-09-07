"use client";

import { useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { callOpeningApi } from "@/lib/api/call-opening";
import { useAuthStore } from "@/lib/store/authStore";
import { handleCallOpeningInstruction } from "@/lib/call-opening/handleCallOpeningInstruction";
import { toast } from "sonner";
import type { CallOpeningEvent } from "@/types/call-opening";

const POLLING_ENABLED = true;
const POLL_INTERVAL_MS = 50000; // 50 seconds

interface UseCallOpeningEventsOptions {
  onEvent?: (event: CallOpeningEvent) => Promise<void>;
}

export function useCallOpeningEvents(
  options: UseCallOpeningEventsOptions = {}
) {
  const { onEvent } = options;
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const hydrated = useAuthStore((state) => state.hydrated);
  const userRole = useAuthStore((state) => state.userRole);
  const tenantId = useAuthStore((state) => state.tenant?.id);
  const router = useRouter();

  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const lastErrorRef = useRef<string | null>(null);
  const isPollingRef = useRef(false);

  // Role gate: only tenant-side users poll. Platform roles never poll.
  const isPlatformRole = userRole === "SUPERADMIN" || userRole === "RESELLER";
  const isTenantRole = !!userRole && !isPlatformRole;

  // Only check Click-to-Call availability for eligible tenant users (avoids unnecessary request for platform roles)
  const canCheckProviders =
    POLLING_ENABLED && isAuthenticated && hydrated && isTenantRole && !!tenantId;

  const callingProvidersQuery = useQuery({
    queryKey: ["call-opening", "calling-providers", tenantId ?? "no-tenant"],
    queryFn: () => callOpeningApi.getCallingProviders(),
    enabled: canCheckProviders,
    staleTime: 2 * 60 * 1000,
    retry: 1,
    refetchOnWindowFocus: false,
  });

  // Fail closed: loading -> no poll, error -> no poll, empty -> no poll
  const isProvidersLoading = canCheckProviders && callingProvidersQuery.isLoading;
  const isClickToCallConfigured =
    canCheckProviders &&
    !callingProvidersQuery.isLoading &&
    !callingProvidersQuery.isError &&
    !!callingProvidersQuery.data &&
    callingProvidersQuery.data.length > 0;

  const shouldPoll =
    POLLING_ENABLED &&
    isAuthenticated &&
    hydrated &&
    isTenantRole &&
    !!tenantId &&
    !isProvidersLoading &&
    isClickToCallConfigured;

  useEffect(() => {
    if (!shouldPoll) {
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }

      return;
    }

    async function pollPendingEvents() {
      // Prevent overlapping requests when the previous poll is still running.
      if (isPollingRef.current) {
        return;
      }

      isPollingRef.current = true;

      try {
        const events = await callOpeningApi.getPendingOpeningEvents();

        await Promise.all(
          events.map(async (event) => {
            if (onEvent) {
              await onEvent(event);
              return;
            }

            const handled = await handleCallOpeningInstruction(
              router,
              event.instruction
            );

            if (handled) {
              await callOpeningApi.markOpeningEventDelivered(event.id);
            }
          })
        );

        lastErrorRef.current = null;
      } catch (error) {
        const message =
          error instanceof Error ? error.message : "Unknown error";

        if (lastErrorRef.current !== message) {
          lastErrorRef.current = message;
          console.error("Call opening event polling failed:", error);
          toast.error("Call event polling failed. Retrying...");
        }
      } finally {
        isPollingRef.current = false;
      }
    }

    void pollPendingEvents();

    pollingRef.current = setInterval(() => {
      void pollPendingEvents();
    }, POLL_INTERVAL_MS);

    return () => {
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }

      isPollingRef.current = false;
    };
  }, [shouldPoll, onEvent, router]);
}