"use client";

import { useEffect, useMemo } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useWorkflowMetadata } from "@/lib/hooks/workflow";

const versionFormSchema = z.object({
  triggerEntityType: z
    .string()
    .trim()
    .min(1, { message: "Trigger entity type is required" })
    .max(100),
  triggerEventType: z
    .string()
    .trim()
    .min(1, { message: "Trigger event type is required" })
    .max(100),
});

export type VersionFormInput = z.input<typeof versionFormSchema>;
export type VersionFormOutput = z.output<typeof versionFormSchema>;

interface CreateVersionDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (values: VersionFormOutput) => void;
  isSubmitting?: boolean;
}

export function CreateVersionDialog({
  open,
  onOpenChange,
  onSubmit,
  isSubmitting = false,
}: CreateVersionDialogProps) {
  const metadataQuery = useWorkflowMetadata();
  const form = useForm<VersionFormInput, unknown, VersionFormOutput>({
    resolver: zodResolver(versionFormSchema),
    defaultValues: { triggerEntityType: "", triggerEventType: "" },
  });
  const selectedEntityType = form.watch("triggerEntityType");

  const selectedEntity = useMemo(
    () =>
      metadataQuery.data?.entities.find(
        (entity) => entity.entityType === selectedEntityType
      ),
    [metadataQuery.data, selectedEntityType]
  );

  useEffect(() => {
    if (open) {
      form.reset({ triggerEntityType: "", triggerEventType: "" });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // Keep event consistent with the selected entity.
  useEffect(() => {
    if (selectedEntity && !selectedEntity.events.some((event) => event.eventType === form.getValues("triggerEventType"))) {
      form.setValue("triggerEventType", "");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedEntity]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>New draft version</DialogTitle>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">
          The draft starts empty. Define what event this workflow listens to.
        </p>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="trigger-entity">When this happens to</Label>
            <Select
              value={selectedEntityType}
              onValueChange={(value) => form.setValue("triggerEntityType", value)}
              disabled={metadataQuery.isLoading}
            >
              <SelectTrigger id="trigger-entity">
                <SelectValue placeholder="Entity" />
              </SelectTrigger>
              <SelectContent>
                {(metadataQuery.data?.entities ?? []).map((entity) => (
                  <SelectItem key={entity.entityType} value={entity.entityType}>
                    {entity.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {form.formState.errors.triggerEntityType && (
              <p className="text-sm text-red-500">
                {form.formState.errors.triggerEntityType.message}
              </p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="trigger-event">Event</Label>
            <Select
              value={form.watch("triggerEventType")}
              onValueChange={(value) => form.setValue("triggerEventType", value)}
              disabled={!selectedEntity}
            >
              <SelectTrigger id="trigger-event">
                <SelectValue placeholder="Event" />
              </SelectTrigger>
              <SelectContent>
                {(selectedEntity?.events ?? []).map((event) => (
                  <SelectItem key={event.eventType} value={event.eventType}>
                    {event.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {form.formState.errors.triggerEventType && (
              <p className="text-sm text-red-500">
                {form.formState.errors.triggerEventType.message}
              </p>
            )}
          </div>

          <div className="flex justify-end">
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Creating..." : "Create draft"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
