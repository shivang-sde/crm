"use client";

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { LeadIngestionMappingForm } from "./LeadIngestionMappingForm";
import {
  LeadIngestionFieldMappingRequest,
  LeadIngestionFieldMappingResponse,
  LeadIngestionSourceField,
  LeadIngestionTargetField,
} from "@/types/acquisition";

interface LeadIngestionMappingDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  configId: string;
  targetFields: LeadIngestionTargetField[];
  sourceFields?: LeadIngestionSourceField[];
  editing?: LeadIngestionFieldMappingResponse | null;
  onSubmit: (values: LeadIngestionFieldMappingRequest) => void;
  isSubmitting?: boolean;
}

export function LeadIngestionMappingDialog({
  open,
  onOpenChange,
  configId,
  targetFields,
  sourceFields,
  editing,
  onSubmit,
  isSubmitting = false,
}: LeadIngestionMappingDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>
            {editing ? "Edit mapping" : "New mapping"}
          </DialogTitle>
        </DialogHeader>
        <LeadIngestionMappingForm
          key={editing?.id ?? "new"}
          configId={configId}
          targetFields={targetFields}
          sourceFields={sourceFields}
          initialValues={editing ?? undefined}
          onSubmit={onSubmit}
          submitLabel={editing ? "Update mapping" : "Create mapping"}
          isSubmitting={isSubmitting}
        />
      </DialogContent>
    </Dialog>
  );
}
