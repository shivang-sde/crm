"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { WorkflowValidationIssue } from "@/types/workflow";

function businessMessageForIssue(issue: WorkflowValidationIssue): { title: string; message: string; hint: string } {
  switch (issue.code) {
    case "WORKFLOW_UNREACHABLE_NODE":
      return { title: "Disconnected step", message: "This step cannot be reached from the start.", hint: "Connect it to a previous step or remove it." };
    case "WORKFLOW_DEAD_END":
      return { title: "Workflow stops here", message: "This step has no next step.", hint: "Add another step or End the workflow." };
    case "WORKFLOW_END_REQUIRED":
      return { title: "Workflow needs an End", message: "Add an End step after the last action.", hint: "Use + Add next step → End." };
    case "WORKFLOW_END_UNREACHABLE":
      return { title: "Cannot reach End", message: "This path does not lead to an End.", hint: "Connect it to an End step." };
    case "WORKFLOW_TRIGGER_REQUIRED":
      return { title: "Missing start", message: "Workflow needs a starting event.", hint: "Create a WHEN step." };
    case "WORKFLOW_TRIGGER_MISMATCH":
      return { title: "Starting event doesn't match", message: "The start event doesn't match the workflow version.", hint: "Edit the WHEN card to fix." };
    case "WORKFLOW_WAIT_RESUME_AT_REQUIRED":
      return { title: "Wait time required", message: "Choose how long to wait.", hint: "Pick a duration or a future date/time." };
    case "WORKFLOW_WAIT_RESUME_AT_PAST":
      return { title: "Wait time is in the past", message: "The selected time is already past.", hint: "Choose a future time, or use a duration. If time is past at execution, it will continue immediately." };
    default:
      return { title: issue.code, message: issue.message, hint: "" };
  }
}

interface WorkflowValidationPanelProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  issues: WorkflowValidationIssue[];
  onSelectIssue?: (issue: WorkflowValidationIssue) => void;
}

export function WorkflowValidationPanel({
  open,
  onOpenChange,
  issues,
  onSelectIssue,
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
          <div className="max-h-72 space-y-2 overflow-auto" role="list">
            {issues.map((issue, index) => {
              const hasTarget = Boolean(issue.nodeId || issue.nodeKey || issue.edgeId);
              const business = businessMessageForIssue(issue);
              return (
                <div key={index} className="rounded-md border p-3 text-sm" role="listitem">
                  <div className="flex items-center justify-between gap-2">
                    <Badge variant="destructive">{business.title}</Badge>
                    {hasTarget && onSelectIssue && (
                      <Button
                        variant="outline"
                        size="xs"
                        onClick={() => onSelectIssue(issue)}
                      >
                        Go to step
                      </Button>
                    )}
                  </div>
                  <p className="mt-1 break-words font-medium">{business.message}</p>
                  <p className="mt-1 text-xs text-muted-foreground">{business.hint}</p>
                  <details className="mt-2">
                    <summary className="cursor-pointer text-[11px] text-muted-foreground">Details</summary>
                    <p className="mt-1 break-words text-xs">{issue.message}</p>
                    <p className="font-mono text-[10px] text-muted-foreground">{issue.code}</p>
                  </details>
                </div>
              );
            })}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
