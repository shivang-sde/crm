"use client";

import * as React from "react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { toast } from "sonner";

interface RecoveryConfirmProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  /** The body is a statement of consequence, not a promise of safety. */
  description: React.ReactNode;
  confirmLabel: string;
  onConfirm: () => Promise<unknown>;
}

/**
 * Confirmation gate for the two recovery actions.
 *
 * The copy is deliberately explicit that the downstream effect may happen again.
 * Neither action claims duplicate execution is impossible:
 *
 *  - DLQ re-drive republishes the message body unchanged, so the canonical eventId
 *    is preserved and the consumer's
 *    ON CONFLICT (workflow_version_id, trigger_event_id) makes a re-drive a no-op
 *    when the trigger already ran. If the original failure happened before the
 *    trigger row was created, the re-drive will create it.
 *  - Outbox requeue returns the event to the retryable state, so the normal
 *    publisher will attempt it again with its existing bounded retry behaviour.
 */
export function RecoveryConfirm({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  onConfirm,
}: RecoveryConfirmProps) {
  const [pending, setPending] = React.useState(false);

  const handleConfirm = async () => {
    setPending(true);
    try {
      await onConfirm();
      onOpenChange(false);
    } catch {
      // The caller's mutation already surfaced a toast with the backend message.
      // Keep the dialog open so the operator can read it and retry deliberately.
    } finally {
      setPending(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={pending ? undefined : onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription asChild>
            <div className="space-y-3 text-sm text-muted-foreground">{description}</div>
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={pending}>
            Cancel
          </Button>
          <Button variant="destructive" onClick={handleConfirm} disabled={pending}>
            {pending ? "Working..." : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function formatTimestamp(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === "") return "—";
  const date = typeof value === "number" ? new Date(value * 1000) : new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString();
}
