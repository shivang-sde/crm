"use client";

import { useEffect } from "react";
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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export const workflowFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, { message: "Workflow name is required" })
    .max(200, { message: "Workflow name cannot exceed 200 characters" }),
});

export type WorkflowFormInput = z.input<typeof workflowFormSchema>;
export type WorkflowFormOutput = z.output<typeof workflowFormSchema>;

interface WorkflowFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (values: WorkflowFormOutput) => void;
  isSubmitting?: boolean;
}

export function WorkflowFormDialog({
  open,
  onOpenChange,
  onSubmit,
  isSubmitting = false,
}: WorkflowFormDialogProps) {
  const form = useForm<WorkflowFormInput, unknown, WorkflowFormOutput>({
    resolver: zodResolver(workflowFormSchema),
    defaultValues: { name: "" },
  });

  useEffect(() => {
    if (open) form.reset({ name: "" });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>New workflow</DialogTitle>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="workflow-name">Name</Label>
            <Input
              id="workflow-name"
              placeholder="Lead Follow-up"
              {...form.register("name")}
            />
            {form.formState.errors.name && (
              <p className="text-sm text-red-500">
                {form.formState.errors.name.message}
              </p>
            )}
          </div>

          <div className="flex justify-end">
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Creating..." : "Create workflow"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
