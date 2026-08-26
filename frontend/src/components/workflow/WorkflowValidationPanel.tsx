"use client";

import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { WorkflowValidationIssue } from "@/types/workflow";

interface WorkflowValidationPanelProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  issues: WorkflowValidationIssue[];
}

export function WorkflowValidationPanel({
  open,
  onOpenChange,
  issues,
}: WorkflowValidationPanelProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>
            {issues.length === 0 ? "Validation" : `${issues.length} validation issue(s)`}
          </DialogTitle>
        </DialogHeader>

        {issues.length === 0 ? (
          <p className="flex items-center gap-2 text-sm">
            ✓ Workflow structure is valid
          </p>
        ) : (
          <div className="max-h-72 space-y-2 overflow-auto">
            {issues.map((issue, index) => (
              <div key={index} className="rounded-md border p-3 text-sm">
                <Badge variant="destructive">{issue.code}</Badge>
                <p className="mt-1 break-words">{issue.message}</p>
              </div>
            ))}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
