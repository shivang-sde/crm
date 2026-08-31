"use client";

import { Zap, GitBranch, PlayCircle, CheckCircle2, Clock, Split } from "lucide-react";
import { WorkflowNodeType } from "@/types/workflow";

interface PaletteItem {
  nodeType: WorkflowNodeType;
  label: string;
  description: string;
}

export const paletteItems: PaletteItem[] = [
  {
    nodeType: "TRIGGER",
    label: "Trigger",
    description: "Starts the workflow when an entity event occurs.",
  },
  {
    nodeType: "CONDITION",
    label: "Condition",
    description: "Branches on TRUE/FALSE field conditions.",
  },
  {
    nodeType: "BRANCH",
    label: "Branch",
    description: "Routes execution through TRUE or FALSE based on conditions.",
  },
  {
    nodeType: "ACTION",
    label: "Action",
    description: "Performs a side effect such as NO_OP or Create Task.",
  },
  {
    nodeType: "WAIT",
    label: "Wait",
    description: "Pause workflow execution until a specified time.",
  },
  {
    nodeType: "END",
    label: "End",
    description: "Terminates the workflow.",
  },
];

export function WorkflowNodePalette({
  disabled,
  onAdd,
}: {
  disabled?: boolean;
  onAdd?: (nodeType: WorkflowNodeType) => void;
}) {
  const handleDragStart = (event: React.DragEvent, nodeType: WorkflowNodeType) => {
    event.dataTransfer.setData("application/workflow-node-type", nodeType);
    event.dataTransfer.effectAllowed = "move";
  };

  const handleActivate = (nodeType: WorkflowNodeType) => {
    if (disabled) return;
    if (onAdd) onAdd(nodeType);
  };

  return (
    <div className="space-y-2">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        Node palette
      </p>
      {paletteItems.map((item) => (
        <div
          key={item.nodeType}
          role="button"
          tabIndex={disabled ? -1 : 0}
          aria-label={`Add ${item.label} node`}
          aria-disabled={disabled ? true : undefined}
          draggable={!disabled}
          onDragStart={(event) => handleDragStart(event, item.nodeType)}
          onClick={() => handleActivate(item.nodeType)}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === " ") {
              event.preventDefault();
              handleActivate(item.nodeType);
            }
          }}
          className={`rounded-lg border p-3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${disabled ? "cursor-not-allowed opacity-50" : "cursor-grab bg-white hover:border-primary/60 active:cursor-grabbing focus-visible:border-primary"}`}
        >
          <div className="flex items-center gap-2">
            {item.nodeType === "TRIGGER" && <Zap className="h-4 w-4 text-amber-500" aria-hidden="true" />}
            {item.nodeType === "CONDITION" && <GitBranch className="h-4 w-4 text-violet-500" aria-hidden="true" />}
            {item.nodeType === "BRANCH" && <Split className="h-4 w-4 text-indigo-500" aria-hidden="true" />}
            {item.nodeType === "ACTION" && <PlayCircle className="h-4 w-4 text-blue-500" aria-hidden="true" />}
            {item.nodeType === "WAIT" && <Clock className="h-4 w-4 text-orange-500" aria-hidden="true" />}
            {item.nodeType === "END" && <CheckCircle2 className="h-4 w-4 text-emerald-500" aria-hidden="true" />}
            <span className="text-sm font-medium">{item.label}</span>
          </div>
          <p className="mt-1 text-xs text-muted-foreground">{item.description}</p>
          <p className="mt-1 text-[11px] text-muted-foreground">Drag to canvas or press Enter to add.</p>
        </div>
      ))}
    </div>
  );
}
