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
              return (
                <div key={index} className="rounded-md border p-3 text-sm" role="listitem">
                  <div className="flex items-center justify-between gap-2">
                    <Badge variant="destructive">{issue.code}</Badge>
                    {hasTarget && onSelectIssue && (
                      <Button
                        variant="outline"
                        size="xs"
                        onClick={() => onSelectIssue(issue)}
                      >
                        Go to node
                      </Button>
                    )}
                  </div>
                  <p className="mt-1 break-words">{issue.message}</p>
                  {(issue.nodeKey || issue.edgeId) && (
                    <p className="mt-1 font-mono text-[11px] text-muted-foreground break-all">
                      {issue.nodeKey ? `node: ${issue.nodeKey}` : `edge: ${issue.edgeId?.slice(0, 8)}`}
                    </p>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
