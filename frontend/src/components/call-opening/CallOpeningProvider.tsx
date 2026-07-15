"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { useCallOpeningEvents } from "@/lib/hooks/useCallOpeningEvents";
import { CallOpeningModal } from "@/components/call-opening/CallOpeningModal";
import { CallOpeningSheet } from "@/components/call-opening/CallOpeningSheet";
import type { CallOpeningEvent, CallOpeningInstruction } from "@/types/call-opening";
import { callOpeningApi } from "@/lib/api/call-opening";
import { toast } from "sonner";

const GLOBAL_SEARCH_ROUTE = "/search";
const SEARCH_ROUTE_AVAILABLE = false;

export function CallOpeningProvider() {
  const router = useRouter();
  const processedEventIdsRef = React.useRef<Set<string>>(new Set());
  const [activeEvent, setActiveEvent] = React.useState<CallOpeningEvent | null>(null);
  const [visibleInstruction, setVisibleInstruction] = React.useState<CallOpeningInstruction | null>(null);
  const [isModalOpen, setIsModalOpen] = React.useState(false);
  const [isSheetOpen, setIsSheetOpen] = React.useState(false);
  const [isToastOpen, setIsToastOpen] = React.useState(false);

  useCallOpeningEvents({ onEvent: handleIncomingEvent });

  async function handleIncomingEvent(event: CallOpeningEvent) {
    if (!event || !event.instruction || processedEventIdsRef.current.has(event.id)) {
      return;
    }

    const instruction = event.instruction;
    const actionType = instruction.actionType || "NO_ACTION";

    if (actionType === "NO_ACTION") {
      const delivered = await markDelivered(event.id);
      if (!delivered) {
        processedEventIdsRef.current.delete(event.id);
      }
      return;
    }

    processedEventIdsRef.current.add(event.id);
    setActiveEvent(event);
    setVisibleInstruction(instruction);

    if (actionType === "OPEN_MODAL") {
      setIsModalOpen(true);
      return;
    }

    if (actionType === "OPEN_SIDEBAR") {
      setIsSheetOpen(true);
      return;
    }

    if (actionType === "OPEN_PAGE") {
      setIsToastOpen(true);
      return;
    }

    if (actionType === "OPEN_CALL_LAYOUT") {
      if (instruction.callId) {
        try {
          router.push(`/calls/active/${instruction.callId}`);
          const delivered = await markDelivered(event.id);
          if (!delivered) {
            toast.error("Call event could not be marked delivered. It may appear again.");
            processedEventIdsRef.current.delete(event.id);
          }
        } catch (error) {
          console.error("Failed navigating to active call", error);
          toast.error("Unable to open active call.");
        } finally {
          setVisibleInstruction(null);
          setActiveEvent(null);
        }
        return;
      }

      setIsSheetOpen(true);
      return;
    }

    toast.info("Received unsupported call action. Please check your call panel.");
  }

  async function markDelivered(eventId: string): Promise<boolean> {
    try {
      await callOpeningApi.markOpeningEventDelivered(eventId);
      return true;
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unknown error";
      console.error("Failed to mark call opening event delivered", message);
      return false;
    }
  }

  async function handleOpenDetails() {
    if (!activeEvent || !visibleInstruction) {
      return;
    }

    const route = visibleInstruction.route || getEntityRoute(visibleInstruction.entityType, visibleInstruction.entityId);
    if (route) {
      try {
        router.push(route);
        const delivered = await markDelivered(activeEvent.id);
        if (!delivered) {
          toast.error("Call event could not be marked delivered. It may appear again.");
          processedEventIdsRef.current.delete(activeEvent.id);
        }
      } catch (error) {
        toast.error("Unable to open call details.");
        console.error("Call opening navigation failed", error instanceof Error ? error.message : error);
      } finally {
        setIsModalOpen(false);
        setIsSheetOpen(false);
        setIsToastOpen(false);
        setVisibleInstruction(null);
        setActiveEvent(null);
      }
      return;
    }

    toast.error("No details route available for this call event.");
  }

  async function handleDismiss() {
    if (!activeEvent) {
      return;
    }

    try {
      const delivered = await markDelivered(activeEvent.id);
      if (!delivered) {
        toast.error("Call event could not be marked delivered. It may appear again.");
        processedEventIdsRef.current.delete(activeEvent.id);
      }
    } finally {
      setVisibleInstruction(null);
      setActiveEvent(null);
      setIsModalOpen(false);
      setIsSheetOpen(false);
      setIsToastOpen(false);
    }
  }

  async function handleOpenPage() {
    if (!activeEvent || !visibleInstruction) {
      return;
    }

    const route = visibleInstruction.route || getEntityRoute(visibleInstruction.entityType, visibleInstruction.entityId);
    if (route) {
      try {
        router.push(route);
        const delivered = await markDelivered(activeEvent.id);
        if (!delivered) {
          toast.error("Call event could not be marked delivered. It may appear again.");
          processedEventIdsRef.current.delete(activeEvent.id);
        }
      } catch (error) {
        toast.error("Unable to navigate to call details.");
        console.error("Call opening route navigation failed", error instanceof Error ? error.message : error);
      } finally {
        setIsToastOpen(false);
        setVisibleInstruction(null);
        setActiveEvent(null);
      }
      return;
    }

    toast.error("No route available for this call event.");
  }

  function getEntityRoute(entityType?: string | null, entityId?: string | null): string | undefined {
    if (!entityType || !entityId) {
      return undefined;
    }

    const lowerType = entityType.toLowerCase();

    switch (lowerType) {
      case "lead":
        return `/leads/${entityId}`;
      case "contact":
        return `/contacts/${entityId}`;
      case "account":
        return `/accounts/${entityId}`;
      case "deal":
        return `/deals/${entityId}`;
      default:
        return undefined;
    }
  }

  function getPhoneNumber(instruction: CallOpeningInstruction): string | undefined {
    if (typeof instruction.metadata?.callerNumber === "string") {
      return instruction.metadata.callerNumber;
    }

    if (typeof instruction.metadata?.calleeNumber === "string") {
      return instruction.metadata.calleeNumber;
    }

    if (typeof instruction.metadata?.phone === "string") {
      return instruction.metadata.phone;
    }

    return undefined;
  }

  async function handleSearchCRM() {
    if (!visibleInstruction) {
      return;
    }

    const phoneNumber = getPhoneNumber(visibleInstruction);
    if (!phoneNumber) {
      toast.error("No phone number available for CRM search.");
      return;
    }

    if (SEARCH_ROUTE_AVAILABLE) {
      router.push(`${GLOBAL_SEARCH_ROUTE}?query=${encodeURIComponent(phoneNumber)}`);
      return;
    }

    toast.error("Search/link workflow will be added in Phase 9C.");
  }

  function getCallLabel(instruction: CallOpeningInstruction) {
    if (instruction.actionType === "OPEN_CALL_LAYOUT") {
      return "Outgoing call connected";
    }
    return instruction.title || "Incoming call";
  }

  return (
    <>
      <CallOpeningModal
        open={isModalOpen}
        instruction={visibleInstruction}
        onOpenDetails={handleOpenDetails}
        onDismiss={handleDismiss}
        onSearchCRM={handleSearchCRM}
      />
      <CallOpeningSheet
        open={isSheetOpen}
        instruction={visibleInstruction}
        onOpenDetails={handleOpenDetails}
        onDismiss={handleDismiss}
        onSearchCRM={handleSearchCRM}
      />
      {visibleInstruction && activeEvent && activeEvent.instruction.actionType === "OPEN_PAGE" && isToastOpen ? (
        <div className="fixed bottom-6 right-6 z-50 w-full max-w-sm rounded-2xl border bg-popover p-4 shadow-xl shadow-black/10">
          <div className="flex items-start gap-3">
            <div className="flex-1">
              <p className="text-sm font-semibold">{getCallLabel(activeEvent.instruction)}</p>
              <p className="mt-1 text-sm text-muted-foreground">
                {activeEvent.instruction.entityType ? `${activeEvent.instruction.entityType} ${activeEvent.instruction.entityId ?? ""}` : "Call details available."}
              </p>
            </div>
            <div className="flex flex-col gap-2">
              <button
                type="button"
                onClick={handleOpenPage}
                className="rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground hover:bg-primary/90"
              >
                Open details
              </button>
              <button
                type="button"
                onClick={handleDismiss}
                className="rounded-md border border-border bg-background px-3 py-2 text-sm hover:bg-muted"
              >
                Dismiss
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}

