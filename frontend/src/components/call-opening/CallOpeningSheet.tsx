"use client";

import { Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { PhoneIncoming, PhoneOutgoing } from "lucide-react";
import type { CallOpeningInstruction } from "@/types/call-opening";

interface CallOpeningSheetProps {
  open: boolean;
  instruction: CallOpeningInstruction | null;
  onOpenDetails: () => void;
  onDismiss: () => void;
  onSearchCRM: () => void;
}

export function CallOpeningSheet({ open, instruction, onOpenDetails, onDismiss, onSearchCRM }: CallOpeningSheetProps) {
  if (!instruction) {
    return null;
  }

  const direction = typeof instruction.metadata?.direction === "string" ? instruction.metadata.direction : undefined;
  const phoneNumber = typeof instruction.metadata?.callerNumber === "string"
    ? instruction.metadata.callerNumber
    : typeof instruction.metadata?.calleeNumber === "string"
      ? instruction.metadata.calleeNumber
      : typeof instruction.metadata?.phone === "string"
        ? instruction.metadata.phone
        : undefined;
  const provider = typeof instruction.metadata?.providerDisplayName === "string"
    ? instruction.metadata.providerDisplayName
    : undefined;
  const resolvedName = typeof instruction.metadata?.resolvedEntityName === "string"
    ? instruction.metadata.resolvedEntityName
    : undefined;
  const resolvedType = instruction.entityType;
  const isUnknownCaller = !resolvedName;
  const title = isUnknownCaller ? "Unknown caller" : instruction.title || "Incoming call";

  return (
    <Sheet open={open} onOpenChange={(isOpen) => !isOpen && onDismiss()}>
      <SheetContent side="right" className="w-full max-w-md">
        <SheetHeader>
          <div className="flex items-center gap-2">
            {instruction.actionType === "OPEN_CALL_LAYOUT" ? (
              <PhoneOutgoing className="h-5 w-5 text-primary" />
            ) : (
              <PhoneIncoming className="h-5 w-5 text-primary" />
            )}
            <SheetTitle>{title}</SheetTitle>
          </div>
          <SheetDescription>{direction ? `${direction.toUpperCase()} call` : "Call event received."}</SheetDescription>
        </SheetHeader>

        <div className="space-y-4 px-4 pb-4">
          {provider ? <Badge variant="secondary">{provider}</Badge> : null}

          <div className="rounded-xl border border-border bg-muted p-4">
            {phoneNumber ? (
              <div className="space-y-1">
                <p className="text-sm font-medium">Phone</p>
                <p className="text-sm text-foreground">{phoneNumber}</p>
              </div>
            ) : null}
            {isUnknownCaller ? (
              <div className="space-y-1 mt-3">
                <p className="text-sm font-medium">Caller</p>
                <p className="text-sm text-foreground">Unknown</p>
              </div>
            ) : null}
            {resolvedName ? (
              <div className="space-y-1 mt-3">
                <p className="text-sm font-medium">Resolved entity</p>
                <p className="text-sm text-foreground">{resolvedName} {resolvedType ? `(${resolvedType})` : ""}</p>
              </div>
            ) : null}
            {instruction.externalCallId ? (
              <div className="space-y-1 mt-3">
                <p className="text-sm font-medium">External call ID</p>
                <p className="text-sm text-muted-foreground break-words">{instruction.externalCallId}</p>
              </div>
            ) : null}
          </div>

          <div className="rounded-xl border border-border bg-background p-4">
            <p className="text-sm text-muted-foreground">Open the details page to continue handling this call.</p>
          </div>
        </div>

        <SheetFooter>
          <div className="flex flex-wrap gap-3 px-4 py-4">
            {isUnknownCaller ? (
              <Button variant="secondary" onClick={onSearchCRM}>Search CRM</Button>
            ) : null}
            <Button variant="default" onClick={onOpenDetails}>Open details</Button>
            <Button variant="outline" onClick={onDismiss}>Dismiss</Button>
          </div>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
