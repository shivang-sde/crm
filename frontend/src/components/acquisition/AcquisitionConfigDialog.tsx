"use client";

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { AcquisitionConfigForm } from "./AcquisitionConfigForm";
import {
  LeadIngestionConfigCreateRequest,
  LeadIngestionConfigResponse,
} from "@/types/acquisition";

interface AcquisitionConfigDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editing?: LeadIngestionConfigResponse | null;
  onSubmit: (values: LeadIngestionConfigCreateRequest) => void;
  isSubmitting?: boolean;
}

export function AcquisitionConfigDialog({
  open,
  onOpenChange,
  editing,
  onSubmit,
  isSubmitting = false,
}: AcquisitionConfigDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {editing ? "Edit source" : "Create webhook source"}
          </DialogTitle>
          <DialogDescription>
            {editing
              ? "Update the source name and status. Only Webhook is activatable."
              : "Step 1 — Choose Webhook (only available transport). Steps 2–5: configure → map → test → activate."}
          </DialogDescription>
        </DialogHeader>
        <AcquisitionConfigForm
          initialValues={editing ?? undefined}
          onSubmit={onSubmit}
          submitLabel={editing ? "Update configuration" : "Create configuration"}
          isSubmitting={isSubmitting}
        />
      </DialogContent>
    </Dialog>
  );
}
